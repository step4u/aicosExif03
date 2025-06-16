package com.example.aicosexif03

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import androidx.lifecycle.lifecycleScope
import com.example.aicosexif03.ExifTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val readImagePerm =
            if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

        setContent {
            var json by remember { mutableStateOf<String>("읽는 중…") }

            /* 최초 1회 실행 */
            LaunchedEffect(Unit) {
                json = try {
                    val testJpg: File = ExifTool.ensureTestImage(this@MainActivity)
                    ExifTool.readExif(this@MainActivity, testJpg)
                } catch (e: Exception) {
                    "오류: ${e.message}"
                }.toString()
            }

            MaterialTheme {
                Text(
                    text = json,   // 결과 JSON 그대로 출력
                )
            }
        }

        lifecycleScope.launch(Dispatchers.IO) { installPerlAssets() }

//        lifecycleScope.launchWhenCreated {
//            withContext(Dispatchers.IO) {
////                prepareAssets()
////                setPerlExecutable()
////                val output = runExifTool()
////                withContext(Dispatchers.Main) {
////                    resultView.text = output
////                }
//                installPerlAssets()
//            }
//        }

//        val exif = ExifTool.readExif(this, "dddd")
    }

    private fun Context.copyAssetDir(assetDir: String, destDir: File) {
        val list = assets.list(assetDir) ?: return
        if (!destDir.exists()) destDir.mkdirs()
        for (name in list) {
            val pathInAssets = if (assetDir.isEmpty()) name else "$assetDir/$name"
            val outFile = File(destDir, name)
            val sub = assets.list(pathInAssets)
            if (sub.isNullOrEmpty()) {
                outFile.parentFile?.mkdirs()
                assets.open(pathInAssets).use { inp ->
                    FileOutputStream(outFile).use { out -> inp.copyTo(out) }
                }
            } else {
                copyAssetDir(pathInAssets, outFile)
            }
        }
    }

    fun Context.installPerlAssets() {
        copyAssetDir("perl5_aarch64", File(filesDir, "perl5_aarch64"))
        copyAssetDir("exiftool_files", File(filesDir, "exiftool_files"))

        val imageFile = File(filesDir, "test.jpg")
        if (!imageFile.exists()) {
            assets.open("test.jpg").use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun prepareAssets() {
        val filesDir = this.filesDir

        val list = assets.list("") ?: arrayOf()

        // exiftool script
        val exifDir = File(filesDir, "exiftool_files")
        if (!exifDir.exists()) {
//            extractTar2("exiftool_files.tar", "")
        }

        val perlDir = File(filesDir, "perl")
        if (!perlDir.exists()) {
//            extractTar("perl5_5.41.8_aarch64_precompiled_so.tar", perlDir)
//            extractTar("perl5_aarch64_so.tar", perlDir)
//            extractTar2("perl5_aarch64_so.tar", perlDir.absolutePath)
        }

        // 테스트 이미지 복사
        val imageFile = File(filesDir, "test.jpg")
        if (!imageFile.exists()) {
            assets.open("test.jpg").use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun extractZip(assetName: String, targetDir: File) {
        assets.open(assetName).use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfter('/', entry.name)
                    if (name.isNotEmpty()) {
                        val outFile = File(targetDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun extractTar(assetName: String, targetDir: File) {
        assets.open(assetName).use { rawStream ->
            BufferedInputStream(rawStream).use { bis ->
                TarArchiveInputStream(bis).use { tis ->
                    var entry = tis.nextTarEntry
                    while (entry != null) {
                        // 경로에서 최상위 디렉토리명 제거
                        val name = entry.name.substringAfter('/', entry.name)
                        if (name.isNotEmpty()) {
                            val outFile = File(targetDir, name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    tis.copyTo(fos)
                                }
                                // 실행 권한 설정
//                                if (entry.name.endsWith(".pl") || name == "perl/bin/perl") {
//                                    outFile.setExecutable(true)
//                                }
                            }
                        }
                        entry = tis.nextTarEntry
                    }
                }
            }
        }
    }

    private fun extractTar2(assetName: String, targetDir: String?) {
        assets.open(assetName).use { raw ->
            TarArchiveInputStream(BufferedInputStream(raw)).use { tis ->
                var entry = tis.nextTarEntry
                while (entry != null) {

                    /* ① realPath : 실제 파일을 쓸 경로 결정  */
                    val realPath: File = if (targetDir.isNullOrEmpty()) {
                        /* 풀 위치 지정을 안 했을 때 → 아카이브 경로 그대로 */
                        File(filesDir, entry.name)
                    } else {
                        /* targetDir 지정 → 최상위 디렉터리만 제거 */
                        val stripped = entry.name.substringAfter('/', entry.name)
                        File(File(targetDir), stripped)
                    }

                    /* ② 파일·디렉터리 생성  */
                    if (entry.isDirectory) {
                        realPath.mkdirs()
                    } else {
                        realPath.parentFile?.mkdirs()
                        FileOutputStream(realPath).use { fos -> tis.copyTo(fos) }
                    }

                    entry = tis.nextTarEntry
                }
            }
        }
    }

    private fun setPerlExecutable() {
//        val perlBinary = File("${filesDir}/perl/bin/perl")
        val perlBinary = File("${filesDir}/perl/bin/perl")
        if (perlBinary.exists()) {
            perlBinary.setExecutable(true, true)
        }

        // 필요에 따라 다른 실행 파일들도 권한 설정
//        val libFiles = File("${filesDir}/perl/local/tmp/perl/bin/perl/lib").listFiles()
//        libFiles?.forEach { file ->
//            if (file.name.endsWith(".so")) {
//                file.setExecutable(true)
//            }
//        }
    }

}
