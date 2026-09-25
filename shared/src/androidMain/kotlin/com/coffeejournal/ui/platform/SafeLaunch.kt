package com.coffeejournal.ui.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Short messages shown when a system screen cannot be used or a picked photo is unusable. */
internal object PlatformMessages {
    const val CAMERA = "카메라를 열 수 없어요."
    const val PHOTO_PICKER = "사진 선택 창을 열 수 없어요."
    const val SAVE_PICKER = "파일 저장 창을 열 수 없어요."
    const val OPEN_PICKER = "파일 선택 창을 열 수 없어요."
    const val SHARE = "공유할 앱을 열 수 없어요."

    /** Web wording (photo upload, reader.onerror). */
    const val PHOTO_READ_FAILED = "사진을 읽는 데 실패했어요."

    /** Web wording (photo upload, img.onerror). */
    const val PHOTO_UNRECOGNIZED = "사진 형식을 인식하지 못했어요. 다른 사진으로 시도해보시겠어요?"
}

internal fun showToast(context: Context, message: String) {
    val app = context.applicationContext
    if (Looper.myLooper() == Looper.getMainLooper()) Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    else Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
}

/**
 * Starts a system screen (camera, photo picker, DocumentsUI, share sheet). When nothing can handle it (the app is
 * disabled, missing or hidden by a work-profile policy) or the launcher is no longer registered, shows
 * [failureMessage] instead of crashing. Returns whether the screen was started.
 */
internal fun launchOrToast(context: Context, failureMessage: String, launch: () -> Unit): Boolean {
    val failure = try {
        launch()
        null
    } catch (e: ActivityNotFoundException) {
        e
    } catch (e: SecurityException) {
        e
    } catch (e: IllegalStateException) {
        e
    }
    if (failure != null) showToast(context, failureMessage)
    return failure == null
}

internal object PhotoBytes {
    /**
     * True when the platform decoder (the one AndroidPhotoStore re-encodes with) can read [bytes]. Anything else would
     * be stored as-is under a .jpg name and show as a blank thumbnail (e.g. HEIC on API 26-27, or an empty capture).
     */
    fun isDecodable(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    /** Null when [bytes] can be stored, otherwise the message to show. */
    fun problem(bytes: ByteArray?): String? = when {
        bytes == null || bytes.isEmpty() -> PlatformMessages.PHOTO_READ_FAILED
        !isDecodable(bytes) -> PlatformMessages.PHOTO_UNRECOGNIZED
        else -> null
    }
}
