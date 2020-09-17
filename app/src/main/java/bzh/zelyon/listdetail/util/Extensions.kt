package bzh.zelyon.listdetail.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.FileProvider
import bzh.zelyon.lib.extension.dpToPx
import bzh.zelyon.lib.extension.isNougat
import bzh.zelyon.lib.ui.component.Popup
import bzh.zelyon.lib.ui.view.activity.AbsActivity
import bzh.zelyon.listdetail.model.Character
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal fun List<Character>.share(absActivity: AbsActivity) {

    val padding = absActivity.dpToPx(64).toInt()
    val progressBar = ProgressBar(absActivity)
    progressBar.isIndeterminate = true
    progressBar.setPadding(0, padding, 0, padding)
    Popup(absActivity, customView = progressBar).show()

    absActivity.ifPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
        val names = ArrayList<String>()
        val uris = ArrayList<Uri>()
        Observable.fromIterable(toMutableList())
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(object : Observer<Character> {

                override fun onSubscribe(d: Disposable) {}

                override fun onNext(character: Character) {

                    names.add(character.name)
                    val file = File(absActivity.externalCacheDir, Uri.parse(character.getPicture()).lastPathSegment)
                    if (!file.exists()) {
                        Picasso.get().load(character.getPicture()).placeholder(GradientDrawable()).get().compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file) as OutputStream)
                    }
                    uris.add(if (isNougat()) FileProvider.getUriForFile(absActivity.applicationContext, absActivity.applicationContext.packageName, file) else Uri.fromFile(file))
                }

                override fun onError(e: Throwable) {}

                override fun onComplete() {

                    Popup.dismiss()

                    absActivity.startActivity(
                        Intent.createChooser(
                            Intent()
                                .setAction(Intent.ACTION_SEND_MULTIPLE)
                                .putExtra(Intent.EXTRA_TEXT, names.joinToString(separator = "\n"))
                                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                .setType("image/png")
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            ""
                        )
                    )
                }
            })
    }
}

internal fun ImageView.setImageUrl(url: String, placeholder: Drawable? = null) {
    var requestCreator = Picasso.get().load(url)
    placeholder?.let {
        requestCreator = requestCreator.placeholder(placeholder)
    }
    requestCreator.into(this)
}

internal fun String.loadImageUrl(runnable: Runnable? = null) {
    Picasso.get().load(this).fetch(object : Callback {
        override fun onSuccess() {
            runnable?.run()
        }
        override fun onError(e: Exception) {
            runnable?.run()
        }
    })
}

//day : black, night : white
internal fun Context.getTextColorPrimary(): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
    return typedValue.resourceId
}