/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.scrollia.app.epub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.scrollia.app.epub.models.EpubBook
import org.scrollia.app.epub.models.EpubChapter
import org.scrollia.app.epub.models.EpubImage
import org.scrollia.app.epub.models.ReaderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.ZipFile


/**
 * Parses an EPUB file and creates an [EpubBook] object.
 */
class EpubParser {

    companion object {
        const val TAG = "EpubParser"
    }

    /**
     * Represents an EPUB document.
     *
     * @param metadata The metadata of the document.
     * @param manifest The manifest of the document.
     * @param spine The spine of the document.
     * @param opfFilePath The file path of the OPF file.
     */
    data class EpubDocument(
        val metadata: Node, val manifest: Node, val spine: Node, val opfFilePath: String
    )

    /**
     * Represents an item in the EPUB manifest.
     *
     * @param id The ID of the item.
     * @param absPath The absolute path of the item.
     * @param mediaType The media type of the item.
     * @param properties The properties of the item.
     */
    data class EpubManifestItem(
        val id: String, val absPath: String, val mediaType: String, val properties: String
    )

    /**
     * Represents an EPUB file.
     *
     * @param absPath The absolute path of the file.
     * @param data The file data.
     */
    data class EpubFile(val absPath: String, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EpubFile

            if (absPath != other.absPath) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = absPath.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Represents the content of a chapter.
     *
     * @param title The title found in the chapter document.
     * @param body The body content of the chapter.
     */
    data class ChapterContent(val title: String, val body: List<ReaderItem>)

    /**
     * Creates an [EpubBook] object from an EPUB file.
     *
     * @param filePath The file path of the EPUB file.
     * @param shouldUseToc Whether to use the table of contents (ToC) file for parsing.
     * @return The [EpubBook] object.
     */
    suspend fun createEpubBook(filePath: String, shouldUseToc: Boolean): EpubBook {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Parsing EPUB file: $filePath")
            val files = getZipFilesFromFile(filePath)
            val document = createEpubDocument(files)
            return@withContext parseAndCreateEbook(files, document, shouldUseToc, filePath)
        }
    }

    /**
     * Creates an [EpubBook] object from an EPUB input stream.
     *
     * Note: Caller is responsible for closing the input stream.
     *
     * @param inputStream The input stream of the EPUB file.
     * @param shouldUseToc Whether to use the table of contents (ToC) file for parsing.
     * @return The [EpubBook] object.
     */
    suspend fun createEpubBook(inputStream: InputStream, shouldUseToc: Boolean): EpubBook {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Parsing EPUB input stream")
            val tempFile = createTempEpubFile(inputStream)
            val files = getZipFilesFromFile(tempFile.absolutePath)
            val document = createEpubDocument(files)
            parseAndCreateEbook(files, document, shouldUseToc, tempFile.absolutePath)
        }
    }

    /**
     * Loads the content of a chapter.
     *
     * @param filePath The file path of the EPUB file.
     * @param chapter The chapter to load the content for.
     * @return The [ChapterContent] of the chapter.
     */
    suspend fun getChapterBody(filePath: String, chapter: EpubChapter): ChapterContent {
        return withContext(Dispatchers.IO) {
            ZipFile(filePath).use { zipFile ->
                val entries = zipFile.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .associateBy { it.name }

                val (fragmentPath, fragmentId) = if ('#' in chapter.absPath) {
                    val (path, id) = chapter.absPath.split("#", limit = 2)
                    path to id
                } else {
                    chapter.absPath to null
                }

                val entry = entries[fragmentPath]
                    ?: return@withContext ChapterContent("", emptyList())

                val data = zipFile.getInputStream(entry).readBytes()

                val epubFiles = entries.mapValues { (path, _) ->
                    EpubFile(absPath = path, data = byteArrayOf())
                }

                val parser = EpubXMLFileParser(
                    fileAbsolutePath = fragmentPath,
                    data = data,
                    zipFile = epubFiles,
                    fragmentId = fragmentId,
                    nextFragmentId = chapter.nextFragmentId
                )

                val res = parser.parseAsDocument()
                ChapterContent(res.title ?: "", res.body)
            }
        }
    }

    /**
     * Loads the image data of an image.
     *
     * @param filePath The file path of the EPUB file.
     * @param imagePath The absolute path of the image in the EPUB.
     * @return The image data.
     */
    suspend fun getImageData(filePath: String, imagePath: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            ZipFile(filePath).use { zipFile ->
                val entry = zipFile.getEntry(imagePath) ?: return@withContext null
                return@withContext zipFile.getInputStream(entry).readBytes()
            }
        }
    }

    /**
     * Peeks the language of an EPUB file without parsing the entire book.
     *
     * @param filePath The file path of the EPUB file.
     * @return The language of the EPUB file.
     */
    fun peekLanguage(filePath: String): String {
        val files = getZipFilesFromFile(filePath)
        val document = createEpubDocument(files)
        return document.metadata.selectFirstChildTag("dc:language")?.textContent ?: "en"
    }

    /**
     * Reads the raw cover image bytes without parsing chapters. Supports the EPUB 2
     * `<meta name="cover">` and EPUB 3 `properties="cover-image"` conventions.
     *
     * @param filePath The file path of the EPUB file.
     * @return The cover image bytes, or null if the book declares no cover.
     */
    fun peekCoverImage(filePath: String): ByteArray? {
        val files = getZipFilesFromFile(filePath)
        val document = createEpubDocument(files)
        val hrefRootPath = File(document.opfFilePath).parentFile ?: File("")
        val manifestItems = getManifestItems(document.manifest, hrefRootPath)
        val coverItem = manifestItems[getMetadataCoverId(document.metadata)]
            ?: manifestItems.values.firstOrNull { it.properties.contains("cover-image") }
            ?: return null
        return ZipFile(filePath).use { zipFile ->
            zipFile.getEntry(coverItem.absPath)?.let { zipFile.getInputStream(it).readBytes() }
        }
    }


    /**
     * Get all of the files located in the EPUB archive.
     * This method is called from [createEpubBook].
     *
     * @param filePath The file path of the EPUB file.
     * @return The EPUB files.
     */
    private fun getZipFilesFromFile(
        filePath: String
    ): Map<String, EpubFile> {
        return ZipFile(filePath).use { zipFile ->
            zipFile.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { entry ->
                    val content = if (isMetadataFile(entry.name)) {
                        zipFile.getInputStream(entry).readBytes()
                    } else {
                        byteArrayOf()
                    }
                    EpubFile(absPath = entry.name, data = content)
                }
                .associateBy { it.absPath }
        }
    }

    /**
     * Creates a temporary EPUB file from an input stream.
     * This method is called from [createEpubBook].
     *
     * @param inputStream The input stream of the EPUB file.
     */
    private suspend fun createTempEpubFile(inputStream: InputStream): File =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("epub_${System.currentTimeMillis()}", ".epub")
            file.deleteOnExit()
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            file
        }

    /**
     * Parses and creates an [EpubBook] object from the EPUB files and document.
     * This function is called from [createEpubBook] and [createEpubBook].
     *
     * @param files The EPUB files.
     * @param document The EPUB document.
     * @param shouldUseToc Whether to use the table of contents (ToC) file for parsing.
     * @param filePath The file path of the EPUB file.
     * @return The [EpubBook] object.
     */
    private suspend fun parseAndCreateEbook(
        files: Map<String, EpubFile>,
        document: EpubDocument,
        shouldUseToc: Boolean,
        filePath: String
    ): EpubBook = withContext(Dispatchers.IO) {
        val metadataTitle =
            document.metadata.selectFirstChildTag("dc:title")?.textContent ?: "Unknown Title"
        val metadataAuthor =
            document.metadata.selectFirstChildTag("dc:creator")?.textContent ?: "Unknown Author"
        val metadataLanguage =
            document.metadata.selectFirstChildTag("dc:language")?.textContent ?: "en"

        val metadataCoverId = getMetadataCoverId(document.metadata)
        val hrefRootPath = File(document.opfFilePath).parentFile ?: File("")

        val manifestItems = getManifestItems(manifest = document.manifest, hrefRootPath)

        // Find the table of contents (toc.ncx) file.
        val tocFileItem = manifestItems.values.firstOrNull {
            it.absPath.endsWith(".ncx", ignoreCase = true)
        }

        // Find the nested navPoints in the table of contents (toc.ncx) file.
        val tocNavPoints = tocFileItem?.let { navItem ->
            val tocFile = files[navItem.absPath]
            val tocDocument = tocFile?.let { parseXMLFile(it.data) }
            findNestedNavPoints((tocDocument?.selectFirstTag("navMap") as Element?))
        }

        // Reading order always comes from the spine. The TOC only supplies titles
        // (and in-file fragment splits when several navPoints share one spine file).
        val tocEntries = if (shouldUseToc) {
            buildTocEntries(tocNavPoints, hrefRootPath)
        } else {
            emptyList()
        }
        Log.d(
            TAG,
            "Parsing chapters from spine (toc titles: ${tocEntries.size}, shouldUseToc: $shouldUseToc)"
        )
        val chapters = parseChaptersFromSpine(
            spine = document.spine,
            manifestItems = manifestItems,
            tocEntries = tocEntries,
        )

        Log.d(TAG, "Parsing images")
        val images = parseImages(manifestItems, files)
        Log.d(TAG, "Parsing cover image")
        val coverImage = parseCoverImage(metadataCoverId, manifestItems, files, filePath)

        Log.d(TAG, "EpubBook created")
        return@withContext EpubBook(
            fileName = metadataTitle.asFileName(),
            title = metadataTitle,
            author = metadataAuthor,
            language = metadataLanguage,
            coverImage = coverImage,
            chapters = chapters,
            images = images,
            filePath = filePath
        )
    }

    /**
     * Check if the path is a metadata file.
     * @param path The path to check.
     * @return True if the path is a metadata file, false otherwise.
     */
    private fun isMetadataFile(path: String): Boolean {
        return path == "META-INF/container.xml" || path.endsWith(".opf") || path.endsWith(".ncx")
    }

    /**
     * Create an [EpubDocument] object from the EPUB files.
     *
     * @param files The EPUB files.
     * @return The [EpubDocument] object.
     */
    @Throws(EpubParserException::class)
    private fun createEpubDocument(files: Map<String, EpubFile>): EpubDocument {
        val container = files["META-INF/container.xml"]
            ?: throw EpubParserException("META-INF/container.xml file missing")

        val opfFilePathAttr = parseXMLFile(container.data)?.selectFirstTag("rootfile")
            ?.getAttributeValue("full-path")
            ?: throw EpubParserException("Invalid container.xml file")

        val opfFilePath = opfFilePathAttr.decodedURL
        val opfFile = files[opfFilePath] ?: throw EpubParserException(".opf file missing")

        val document = parseXMLFile(opfFile.data)
            ?: throw EpubParserException(".opf file failed to parse data")
        val metadata = document.selectFirstTag("metadata")
            ?: document.selectFirstTag("opf:metadata")
            ?: throw EpubParserException(".opf file metadata section missing")
        val manifest = document.selectFirstTag("manifest")
            ?: document.selectFirstTag("opf:manifest")
            ?: throw EpubParserException(".opf file manifest section missing")
        val spine = document.selectFirstTag("spine")
            ?: document.selectFirstTag("opf:spine")
            ?: throw EpubParserException(".opf file spine section missing")

        return EpubDocument(metadata, manifest, spine, opfFilePath)
    }

    /**
     * Get the cover ID from the EPUB metadata.
     * This method is called from [parseAndCreateEbook].
     *
     * @param metadata The metadata of the EPUB document.
     * @return The cover ID.
     */
    private fun getMetadataCoverId(metadata: Node): String? {
        return metadata.selectChildTag("meta")
            .ifEmpty { metadata.selectChildTag("opf:meta") }
            .find { it.getAttributeValue("name") == "cover" }?.getAttributeValue("content")
    }

    /**
     * Get the manifest items from the EPUB manifest.
     * This method is called from [parseAndCreateEbook].
     *
     * @param manifest The manifest of the EPUB document.
     * @param hrefRootPath The root path of the href attribute.
     * @return The manifest items.
     */
    private fun getManifestItems(
        manifest: Node,
        hrefRootPath: File
    ): Map<String, EpubManifestItem> {
        return manifest.selectChildTag("item")
            .ifEmpty { manifest.selectChildTag("opf:item") }
            .map {
                EpubManifestItem(
                    id = it.getAttribute("id"),
                    absPath = it.getAttribute("href").decodedURL.hrefAbsolutePath(hrefRootPath),
                    mediaType = it.getAttribute("media-type"),
                    properties = it.getAttribute("properties")
                )
            }.associateBy { it.id }
    }

    /**
     * Find all the nested navPoints in the table of contents (ToC) file.
     * This method is called from [parseAndCreateEbook].
     *
     * @param element The element to search for nested navPoints.
     * @return The list of nested navPoints.
     */
    private fun findNestedNavPoints(element: Element?): List<Element> {
        val navPoints = mutableListOf<Element>()
        if (element == null) {
            return navPoints
        }
        if (element.tagName == "navPoint") {
            navPoints.add(element)
        }
        // Recursively search for nested navPoints
        for (child in element.childElements) {
            navPoints.addAll(findNestedNavPoints(child))
        }
        return navPoints
    }

    /**
     * Generate a unique ID for a chapter.
     *
     * @return The generated ID.
     */
    private fun generateId(): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = ThreadLocalRandom.current().nextInt(1000, 9999)
        return "$timestamp-$randomSuffix"
    }

    private data class TocEntry(
        val path: String,
        val fragmentId: String?,
        val title: String,
    )

    /**
     * Flatten TOC navPoints into path/fragment/title rows for spine labelling.
     */
    private fun buildTocEntries(
        tocNavPoints: List<Element>?,
        hrefRootPath: File,
    ): List<TocEntry> {
        if (tocNavPoints.isNullOrEmpty()) return emptyList()
        return tocNavPoints.mapIndexedNotNull { index, navPoint ->
            val title =
                navPoint.selectFirstChildTag("navLabel")?.selectFirstChildTag("text")?.textContent
                    ?.takeIf { it.isNotEmpty() } ?: "Chapter ${index + 1}"
            val chapterSrc = navPoint.selectFirstChildTag("content")?.getAttributeValue("src")
                ?.decodedURL?.hrefAbsolutePath(hrefRootPath) ?: return@mapIndexedNotNull null
            val path = chapterSrc.substringBefore('#')
            val fragmentId = chapterSrc.substringAfter('#', missingDelimiterValue = "")
                .ifEmpty { null }
            TocEntry(path = path, fragmentId = fragmentId, title = title)
        }
    }

    /**
     * Build the chapter list from the spine (complete reading order). TOC entries
     * only rename chapters and, when several share one file with fragment ids,
     * split that file the way the TOC describes.
     */
    private fun parseChaptersFromSpine(
        spine: Node,
        manifestItems: Map<String, EpubManifestItem>,
        tocEntries: List<TocEntry>,
    ): List<EpubChapter> {
        val tocByPath = tocEntries.groupBy { it.path }
        val chapterExtensions = listOf("xhtml", "xml", "html", "htm").map { ".$it" }
        val chapters = mutableListOf<EpubChapter>()
        var chapterIndex = 0

        val spineItems = spine.selectChildTag("itemref")
            .ifEmpty { spine.selectChildTag("opf:itemref") }
            .mapNotNull { manifestItems[it.getAttribute("idref")] }
            .filter { item ->
                chapterExtensions.any {
                    item.absPath.endsWith(it, ignoreCase = true)
                } || item.mediaType.startsWith("image/")
            }

        for (item in spineItems) {
            if (item.mediaType.startsWith("image/")) {
                chapters.add(
                    EpubChapter(
                        chapterId = generateId(),
                        absPath = "image_${item.absPath}",
                        title = "Chapter ${chapterIndex + 1}",
                        body = emptyList(),
                    )
                )
                chapterIndex += 1
                continue
            }

            val path = item.absPath
            val entriesForFile = tocByPath[path].orEmpty()
            val splitByFragments =
                entriesForFile.size >= 2 && entriesForFile.any { it.fragmentId != null }

            if (splitByFragments) {
                entriesForFile.forEachIndexed { index, entry ->
                    val next = entriesForFile.getOrNull(index + 1)
                    val nextFragmentId =
                        next?.fragmentId?.takeIf { next.path == path && entry.fragmentId != null }
                    val absPath =
                        if (entry.fragmentId != null) "$path#${entry.fragmentId}" else path
                    chapters.add(
                        EpubChapter(
                            chapterId = generateId(),
                            absPath = absPath,
                            title = entry.title,
                            body = emptyList(),
                            nextFragmentId = nextFragmentId,
                        )
                    )
                    chapterIndex += 1
                }
            } else {
                val title = entriesForFile.firstOrNull()?.title ?: "Chapter ${chapterIndex + 1}"
                // Whole spine file: never drop a file that the TOC forgot to list.
                chapters.add(
                    EpubChapter(
                        chapterId = generateId(),
                        absPath = path,
                        title = title,
                        body = emptyList(),
                    )
                )
                chapterIndex += 1
            }
        }

        return chapters
    }

    /**
     * Parse the EPUB images.
     * This method is called from [parseAndCreateEbook].
     *
     * @param manifestItems The manifest items.
     * @param files The EPUB files.
     * @return The list of parsed images.
     */
    private fun parseImages(
        manifestItems: Map<String, EpubManifestItem>, files: Map<String, EpubFile>
    ): List<EpubImage> {
        val imageExtensions =
            listOf("gif", "raw", "png", "jpg", "jpeg", "webp", "svg").map { ".$it" }
        val unlistedImages = files.asSequence().filter { (_, file) ->
            imageExtensions.any { file.absPath.endsWith(it, ignoreCase = true) }
        }.map { (_, file) ->
            EpubImage(absPath = file.absPath, image = null)
        }

        val listedImages =
            manifestItems.asSequence()
                .map { it.value }.filter { it.mediaType.startsWith("image") }
                .mapNotNull { files[it.absPath] }
                .map { EpubImage(absPath = it.absPath, image = null) }

        return (listedImages + unlistedImages).distinctBy { it.absPath }.toList()
    }

    /**
     * Parse the EPUB cover image.
     * This method is called from [parseAndCreateEbook].
     *
     * @param metadataCoverId The cover ID from the EPUB metadata.
     * @param manifestItems The manifest items.
     * @param files The EPUB files.
     * @param filePath The file path of the EPUB file.
     * @return The cover image.
     */
    private suspend fun parseCoverImage(
        metadataCoverId: String?,
        manifestItems: Map<String, EpubManifestItem>,
        files: Map<String, EpubFile>,
        filePath: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val coverImageItem = manifestItems[metadataCoverId]?.let { files[it.absPath] }
        val coverImageData = coverImageItem?.let {
            if (it.data.isNotEmpty()) {
                it.data
            } else {
                ZipFile(filePath).use { zipFile ->
                    val entry = zipFile.getEntry(it.absPath)
                    zipFile.getInputStream(entry).readBytes()
                }
            }
        }

        if (coverImageData != null) {
            BitmapFactory.decodeByteArray(coverImageData, 0, coverImageData.size)
        } else {
            Log.e(TAG, "Cover image not found")
            null
        }
    }
}
