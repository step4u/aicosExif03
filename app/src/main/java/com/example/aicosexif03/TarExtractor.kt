import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.*

object TarExtractor {

    /**
     * assets에 있는 tar/tar.gz 파일을 destDir에 해제
     * @param context    컨텍스트 (에셋 접근용)
     * @param assetName  에셋 파일명 (예: "archives/data.tar.gz")
     * @param destDir    출력 디렉터리 (null인 경우 현재 디렉터리 사용)
     * @param strip      제거할 상위 디렉터리 단계 (기본값 1, 0이면 생략 가능)
     */
    suspend fun extract(
        context: Context,
        assetName: String,
        destDir: File? = null,
        strip: Int = 0
    ) = withContext(Dispatchers.IO) {

        // destDir가 null이면 현재 디렉터리 사용
        val outputDir = destDir ?: File(System.getProperty("user.dir"))

        if (!outputDir.exists()) outputDir.mkdirs()

        // 에셋 스트림 열기
        val assetStream = try {
            context.assets.open(assetName)
        } catch (e: IOException) {
            throw IllegalArgumentException("에셋을 찾을 수 없습니다: $assetName", e)
        }

        // 압축 방식 판별
        val inputStream = if (assetName.endsWith(".gz")) {
            GzipCompressorInputStream(BufferedInputStream(assetStream))
        } else {
            BufferedInputStream(assetStream)
        }

        TarArchiveInputStream(inputStream).use { tarIn ->

            var entry = tarIn.nextTarEntry
            val buf = ByteArray(32 * 1024)

            while (entry != null) {

                // 경로 정규화 및 strip 적용
                val sanitizedPath = if (strip > 0) {
                    entry.name.replace('\\', '/')
                        .split('/')
                        .drop(strip)
                        .joinToString("/")
                } else {
                    // strip이 0이면 원본 경로 유지
                    entry.name.replace('\\', '/')
                }

                if (sanitizedPath.isNotEmpty()) {
                    val outFile = File(outputDir, sanitizedPath).canonicalFile

                    // 경로 탈출 방지
                    if (!outFile.path.startsWith(outputDir.canonicalPath)) {
                        throw IOException("잘못된 경로 접근: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var bytesRead: Int
                            while (tarIn.read(buf).also { bytesRead = it } > 0) {
                                out.write(buf, 0, bytesRead)
                            }
                        }
                        outFile.setLastModified(entry.modTime.time)
                    }
                }
                entry = tarIn.nextTarEntry
            }
        }
    }
}
