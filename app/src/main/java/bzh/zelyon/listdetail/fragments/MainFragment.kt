package bzh.zelyon.listdetail.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.AppCompatTextView
import android.transition.TransitionInflater
import android.view.View
import android.view.ViewAnimationUtils
import bzh.zelyon.listdetail.*
import bzh.zelyon.listdetail.models.Character
import bzh.zelyon.listdetail.utils.Adapter
import bzh.zelyon.listdetail.utils.DB
import bzh.zelyon.listdetail.views.FilterView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.android.synthetic.main.fragment_main.*

class MainFragment: AbsToolBarFragment() {

    companion object {

        const val LIST = "LIST"
        const val SEARCH_APPLY_SAVE = "SEARCH_APPLY_SAVE"
        const val HOUSES_APPLY_SAVE = "HOUSES_APPLY_SAVE"
        const val OTHERS_APPLY_SAVE = "OTHERS_APPLY_SAVE"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private var modeList = true

    private var selectedCharacters: ArrayList<Character> = ArrayList()
    private var searchApply = ""
    private var housesApply: ArrayList<Long> = ArrayList()
    private var othersApply: ArrayList<String> = ArrayList()

    private lateinit var characterAdapter: CharacterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedElementEnterTransition = TransitionInflater.from(mainActivity).inflateTransition(R.transition.enter_transition)
        exitTransition = TransitionInflater.from(mainActivity).inflateTransition(R.transition.exit_transition)
        postponeEnterTransition()
        startPostponedEnterTransition()

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        modeList = sharedPreferences.getBoolean(LIST, true)

        if (savedInstanceState != null) {

            searchApply = savedInstanceState.getString(SEARCH_APPLY_SAVE)
            savedInstanceState.getLongArray(HOUSES_APPLY_SAVE).toCollection(housesApply)
            savedInstanceState.getStringArray(OTHERS_APPLY_SAVE).toCollection(othersApply)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {

        outState.putString(SEARCH_APPLY_SAVE, searchApply)
        outState.putLongArray(HOUSES_APPLY_SAVE, housesApply.toLongArray())
        outState.putStringArray(OTHERS_APPLY_SAVE, othersApply.toTypedArray())

        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        characterAdapter = CharacterAdapter(mainActivity, if (modeList) R.layout.item_list else R.layout.item_module)
        recycler_view.init(if(modeList) 1 else 3)
        recycler_view.adapter = characterAdapter

        action_mode_toolbar.setNavigationIcon(R.drawable.ic_close)
        action_mode_toolbar.inflateMenu(R.menu.character)
        action_mode_toolbar.setNavigationOnClickListener {

            showActionMode(false)
        }
        action_mode_toolbar.setOnMenuItemClickListener {

            selectedCharacters.share(mainActivity)

            showActionMode(false)

            false
        }

        filter_toolbar.setNavigationIcon(R.drawable.ic_close)
        filter_toolbar.inflateMenu(R.menu.filter)
        filter_toolbar.setNavigationOnClickListener {

            showSearchAndFilter(false)
        }
        filter_toolbar.setOnMenuItemClickListener {

            when(it.itemId) {

                R.id.restore -> {

                    search_view.setQuery(searchApply, false)
                    house_filter_view.restore()
                    other_filter_view.restore()
                }

                R.id.clear -> {

                    search_view.setQuery("", false)
                    house_filter_view.clear()
                    other_filter_view.clear()
                }
            }
            false
        }

        search_view.setQuery(searchApply, false)

        val otherItems = arrayListOf(
            FilterView.Item(Character.GENDER_MALE, getString(R.string.fragment_character_gender_man), mainActivity.drawableResToDrawable(R.drawable.ic_male, R.color.black)),
            FilterView.Item(Character.GENDER_FEMALE, getString(R.string.fragment_character_gender_woman), mainActivity.drawableResToDrawable( R.drawable.ic_female, R.color.black)),
            FilterView.Item(Character.ALIVE, getString(R.string.fragment_character_alive_man), mainActivity.drawableResToDrawable(R.drawable.ic_alive, R.color.black)),
            FilterView.Item(Character.DEAD, getString(R.string.fragment_character_dead_man), mainActivity.drawableResToDrawable(R.drawable.ic_dead, R.color.black))
        )

        (other_filter_view as FilterView<String>).load(otherItems, othersApply)

        loadCharacters()

        loadHouses()
    }

    override fun getLayoutId() = R.layout.fragment_main

    override fun onIdClick(id: Int) {

        when (id) {
            R.id.close -> {

                search_view.setQuery(searchApply, false)
                house_filter_view.restore()
                other_filter_view.restore()

                showSearchAndFilter(false)
            }
            R.id.valid -> {

                searchApply = search_view.query.toString()
                housesApply = (house_filter_view as FilterView<Long>).getSelectedAndApply()
                othersApply = (other_filter_view as FilterView<String>).getSelectedAndApply()

                loadCharacters()

                showSearchAndFilter(false)
            }
            R.id.mode -> {

                modeList = !modeList

                sharedPreferences.edit().putBoolean(LIST, modeList).apply()

                characterAdapter.idItemLayout = if (modeList) R.layout.item_list else R.layout.item_module
                recycler_view.init(if (modeList) 1 else 3)
                recycler_view.adapter = characterAdapter

                onMenuCreated()
            }
            R.id.search -> {

                showSearchAndFilter(true)
            }
        }
    }

    override fun getTitle() = getString(R.string.fragment_main_title)

    override fun showBack() = false

    override fun getIdMenu() = R.menu.main

    override fun onMenuCreated() {

        menu?.findItem(R.id.mode)?.setIcon(if (modeList) R.drawable.ic_module else R.drawable.ic_list)
    }

    fun loadCharacters() {

        lateinit var characters: List<Character>

        var man = othersApply.contains(Character.GENDER_MALE)
        var female = othersApply.contains(Character.GENDER_FEMALE)
        var dead = othersApply.contains(Character.DEAD)
        var alive = othersApply.contains(Character.ALIVE)

        if(searchApply.isNotBlank() && housesApply.isNotEmpty() && man != female && dead != alive) {

            characters = DB.getCharacterDao().getByNameAndHouseAndGenderAndStatus(searchApply, housesApply, man, dead)
        }
        else if(searchApply.isNotBlank() && housesApply.isNotEmpty() && man != female && dead == alive) {

            characters = DB.getCharacterDao().getByNameAndHouseAndGender(searchApply, housesApply, man)
        }
        else if(searchApply.isNotBlank() && housesApply.isNotEmpty() && man == female && dead != alive) {

            characters = DB.getCharacterDao().getByNameAndHouseAndStatus(searchApply, housesApply, dead)
        }
        else if(searchApply.isNotBlank() && housesApply.isNotEmpty() && man == female && dead == alive) {

            characters = DB.getCharacterDao().getByNameAndHouse(searchApply, housesApply)
        }
        else if(searchApply.isNotBlank() && housesApply.isEmpty() && man != female && dead != alive) {

            characters = DB.getCharacterDao().getByNameAndGenderAndStatus(searchApply, man, dead)
        }
        else if(searchApply.isNotBlank() && housesApply.isEmpty() && man != female && dead == alive) {

            characters = DB.getCharacterDao().getByNameAndGender(searchApply, man)
        }
        else if(searchApply.isNotBlank() && housesApply.isEmpty() && man == female && dead != alive) {

            characters = DB.getCharacterDao().getByNameAndStatus(searchApply, dead)
        }
        else if(searchApply.isNotBlank() && housesApply.isEmpty() && man == female && dead == alive) {

            characters = DB.getCharacterDao().getByName(searchApply)
        }
        else if(searchApply.isBlank() && housesApply.isNotEmpty() && man != female && dead != alive) {

            characters = DB.getCharacterDao().getByHouseAndGenderAndStatus(housesApply, man, dead)
        }
        else if(searchApply.isBlank() && housesApply.isNotEmpty() && man != female && dead == alive) {

            characters = DB.getCharacterDao().getByHouseAndGender(housesApply, man)
        }
        else if(searchApply.isBlank() && housesApply.isNotEmpty() && man == female && dead != alive) {

            characters = DB.getCharacterDao().getByHouseAndStatus(housesApply, dead)
        }
        else if(searchApply.isBlank() && housesApply.isNotEmpty() && man == female && dead == alive) {

            characters = DB.getCharacterDao().getByHouse(housesApply)
        }
        else if(searchApply.isBlank() && housesApply.isEmpty() && man != female && dead != alive) {

            characters = DB.getCharacterDao().getByGenderAndStatus(man, dead)
        }
        else if(searchApply.isBlank() && housesApply.isEmpty() && man != female && dead == alive) {

            characters = DB.getCharacterDao().getByGender(man)
        }
        else if(searchApply.isBlank() && housesApply.isEmpty() && man == female && dead != alive) {

            characters = DB.getCharacterDao().getByStatus(dead)
        }
        else {

            characters = DB.getCharacterDao().getAll()
        }

        characterAdapter.datas = characters
    }

    fun loadHouses() {

        val houses= DB.getHouseDao().getAll()
        val items = ArrayList<FilterView.Item<Long>>()

        for (house in houses) {

            Picasso.get().load(house.getThumbnail()).placeholder(GradientDrawable()).into(object : Target {

                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {

                    items.add(FilterView.Item(house.id, house.label, BitmapDrawable(resources, bitmap)))

                    if (items.size == houses.size) {

                        (house_filter_view as FilterView<Long>).load(items, housesApply)
                    }
                }

                override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {}

                override fun onPrepareLoad(placeHolderDrawable: Drawable) {}
            })
        }
    }

    private fun showSearchAndFilter(show: Boolean) {

        filter_layout.visibility = View.VISIBLE

        val radius = Math.sqrt((filter_layout.height * filter_layout.height + filter_layout.height * filter_layout.height).toDouble()).toFloat()

        val circularReveal= ViewAnimationUtils.createCircularReveal(filter_layout, filter_layout.height,0, if (show) 0f else radius, if (show) radius else 0f).setDuration(600)
        circularReveal.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)

                filter_layout.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
        })
        circularReveal.start()
    }

    private fun showActionMode(show: Boolean) {

        if (show) {

            action_mode_toolbar.title = selectedCharacters.size.toString()
        }
        else {

            selectedCharacters.clear()
        }

        characterAdapter.notifyDataSetChanged()

        action_mode_toolbar.visibility = if (show) View.VISIBLE else View.GONE
    }

    inner class CharacterAdapter constructor(context: Context, idItemLayout: Int) : Adapter<Character>(context, idItemLayout) {

        override fun onItemFill(itemView: View, datas: List<Character>, position: Int) {

            val character= datas[position]

            val characterIsSelected = selectedCharacters.contains(character)

            val image= itemView.findViewById<AppCompatImageView>(R.id.image)
            image.visibility = if (modeList && characterIsSelected) View.GONE else View.VISIBLE
            image.scaleX = if (!modeList && characterIsSelected) .8f else 1f
            image.scaleY = if (!modeList && characterIsSelected) .8f else 1f
            image.transitionName = character.id.toString()
            image.setImageUrl(character.getThumbnail())

            val badge = itemView.findViewById<AppCompatImageView>(R.id.badge)
            badge.visibility = if (modeList && characterIsSelected || !modeList && !selectedCharacters.isEmpty()) View.VISIBLE else View.GONE
            badge.setImageResource(if (characterIsSelected) R.drawable.ic_check else R.drawable.ic_uncheck)
            badge.setColorFilter(mainActivity.colorResToColorInt(if (characterIsSelected) R.color.green else if (modeList) R.color.black else R.color.white))

            itemView.findViewById<AppCompatTextView>(R.id.name).text = character.name
        }

        override fun onItemClick(itemView: View, datas: List<Character>, position: Int) {

            val character= datas[position]

            if (action_mode_toolbar.visibility == View.GONE) {

                val image= itemView.findViewById<AppCompatImageView>(R.id.image)

                mainActivity.setFragment(CharacterFragment.newInstance(character.id, (image.drawable as BitmapDrawable).bitmap), image)
            }
            else {

                selectCharacter(itemView, character)
            }
        }

        override fun onItemLongClick(itemView: View, datas: List<Character>, position: Int) {

            val character= datas[position]

            selectCharacter(itemView, character)
        }

        private fun selectCharacter(itemView: View, character: Character) {

            if (selectedCharacters.contains(character)) {

                selectedCharacters.remove(character)
            }
            else {

                selectedCharacters.add(character)
            }

            if (modeList) {

                showActionMode(selectedCharacters.isNotEmpty())
            }
            else {

                val characterIsSelected= selectedCharacters.contains(character)

                val image= itemView.findViewById<AppCompatImageView>(R.id.image)
                val scale= if (characterIsSelected) .8f else 1f
                image.animate().scaleY(scale).scaleX(scale).setDuration(200L).withEndAction {

                    showActionMode(selectedCharacters.isNotEmpty())
                }
            }
        }
    }
}