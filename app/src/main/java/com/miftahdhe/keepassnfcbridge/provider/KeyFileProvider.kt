package com.miftahdhe.keepassnfcbridge.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.miftahdhe.keepassnfcbridge.R
import com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager
import java.io.FileNotFoundException
import java.io.IOException

class KeyFileProvider : DocumentsProvider() {

    companion object {
        private const val ROOT_ID = "root"
        private const val DOC_ID_KEYFILE = "ram_keyfile.bin"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "KeePass NFC Bridge")
        row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (documentId == ROOT_ID) {
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_ID)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "KeePass NFC Bridge")
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, 0)
        } else if (documentId == DOC_ID_KEYFILE) {
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_KEYFILE)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream")
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "KPNFC_Keyfile.bin")
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            val key = KeySessionManager.getActiveKey()
            row.add(DocumentsContract.Document.COLUMN_SIZE, key?.size ?: 0)
        }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (parentDocumentId == ROOT_ID) {
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_KEYFILE)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream")
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "KPNFC_Keyfile.bin")
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            val key = KeySessionManager.getActiveKey()
            row.add(DocumentsContract.Document.COLUMN_SIZE, key?.size ?: 0)
        }
        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (documentId == DOC_ID_KEYFILE) {
            val key = KeySessionManager.getActiveKey()
                ?: throw FileNotFoundException("Session Expired or No Key Loaded")

            val pipe = ParcelFileDescriptor.createReliablePipe()
            val readFd = pipe[0]
            val writeFd = pipe[1]

            Thread {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { os ->
                        os.write(key)
                        os.flush()
                    }
                } catch (e: IOException) {
                    try {
                        writeFd.closeWithError(e.message)
                    } catch (ignored: IOException) {}
                }
            }.start()

            return readFd
        }
        throw FileNotFoundException("Unknown document ID: $documentId")
    }
}
