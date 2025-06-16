package com.example.aicosexif03

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ExifTool {
    init { System.loadLibrary("exiftool_jni") }

    /* ───────── 기존 JNI 연결 ───────── */
    @JvmStatic private external fun runExifTool(
        imagePath: String,
        workDir: String
    ): String

    /* exiftool_files 디렉터리를 1회 복사 (변경 없음) */
    fun ensureWorkDir(context: Context): File {
        val dest = File(context.filesDir, "exiftool_files")
        if (dest.exists()) return dest

//        ZipInputStream(context.assets.open("exiftool_files.zip")).use { zis ->
//            var e = zis.nextEntry
//            while (e != null) {
//                val out = File(dest, e.name)
//                if (e.isDirectory) out.mkdirs()
//                else {
//                    out.parentFile?.mkdirs()
//                    FileOutputStream(out).use { fos -> zis.copyTo(fos) }
//                }
//                e = zis.nextEntry
//            }
//        }
        return dest
    }

    /* ───────── 새로 추가: test.jpg 확보 ───────── */
    fun ensureTestImage(context: Context): File {
        val img = File(context.filesDir, "test.jpg")
        if (!img.exists()) {                 // 없으면 assets/test.jpg 복사
            context.assets.open("test.jpg").use { input ->
                FileOutputStream(img).use { output -> input.copyTo(output) }
            }
        }
        return img
    }

    /* public API: 파일 하나 분석 */
    fun readExif(context: Context, image: File): String {
        val workDir = ensureWorkDir(context)
        return runExifTool(image.absolutePath, workDir.absolutePath)
    }
}
