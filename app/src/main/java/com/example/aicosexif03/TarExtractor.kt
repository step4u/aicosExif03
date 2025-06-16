package com.example.aicosexif03

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.*

object TarExtractor {

    /**
     * ddd.tar.gz 내용을 destDir 에 풀되, 경로의 최상위 디렉터리 1단계를 제거한다.
     *
     * @param srcTarGz   .tar.gz 파일
     * @param destDir    출력 디렉터리 (미존재 시 생성)
     */
    suspend fun extractStrip1(srcTarGz: File, destDir: File) =
        withContext(Dispatchers.IO) {

            require(srcTarGz.exists()) { "파일이 존재하지 않습니다: ${srcTarGz.path}" }
            if (!destDir.exists()) destDir.mkdirs()

            TarArchiveInputStream(
                GzipCompressorInputStream(BufferedInputStream(FileInputStream(srcTarGz)))
            ).use { tarIn ->

                var entry = tarIn.nextTarEntry
                val buf = ByteArray(32 * 1024)

                while (entry != null) {

                    // 1) 경로 안전성 확보 ─ ../ 등 차단
                    val rawName = entry.name
                    val cleanName = rawName.replace('\\', '/')
                        .split('/').drop(1)           // strip-components=1
                        .joinToString("/")

                    if (cleanName.isNotEmpty()) {
                        val outFile = File(destDir, cleanName).canonicalFile

                        // /destDir/ 바깥으로 탈출 시도 방지
                        if (!outFile.path.startsWith(destDir.canonicalPath)) {
                            throw IOException("경로 탈출 시도가 감지되었습니다: $rawName")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                var n: Int
                                while (tarIn.read(buf).also { n = it } > 0) {
                                    out.write(buf, 0, n)
                                }
                            }
                            // 권한·타임스탬프 복원(선택)
                            outFile.setLastModified(entry.modTime.time)
                            // 파일 권한은 Android 파일시스템상 0666·0777 마스킹이 적용됨
                        }
                    }
                    entry = tarIn.nextTarEntry
                }
            }
        }
}
