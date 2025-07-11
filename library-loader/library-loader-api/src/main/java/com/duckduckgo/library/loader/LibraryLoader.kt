package com.duckduckgo.library.loader

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("NoSystemLoadLibrary")
object LibraryLoader {

    /**
     * Load a native library synchronously on the current thread.
     */
    @JvmStatic
    fun loadLibrary(context: Context, name: String) {
        System.loadLibrary(name)
    }

    /**
     * Load a native library asynchronously, and notify via a listener.
     * The actual library loading is done on the **main thread** to avoid crashes
     * due to page alignment issues in background threads.
     */
    @JvmStatic
    fun loadLibrary(context: Context, name: String, listener: LibraryLoaderListener) {
        // Run the loading operation on the main thread
        Thread {
            try {
                System.loadLibrary(name)
                listener.success()
            } catch (e: UnsatisfiedLinkError) {
                listener.failure(e)
            } catch (e: SecurityException) {
                listener.failure(e)
            } catch (e: NullPointerException) {
                listener.failure(e)
            } catch (e: Exception) {
                listener.failure(e)
            }
        }.start()
    }

    interface LibraryLoaderListener {
        fun success()
        fun failure(throwable: Throwable)
    }
}
