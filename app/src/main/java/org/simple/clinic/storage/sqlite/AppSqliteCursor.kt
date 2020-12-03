package org.simple.clinic.storage.sqlite

import io.requery.android.database.CursorWindow
import io.requery.android.database.sqlite.SQLiteCursor
import io.requery.android.database.sqlite.SQLiteCursorDriver
import io.requery.android.database.sqlite.SQLiteQuery

class AppSqliteCursor(
    driver: SQLiteCursorDriver,
    editTable: String,
    query: SQLiteQuery
) : SQLiteCursor(driver, editTable, query) {

  override fun clearOrCreateWindow(name: String?) {
    if (mWindow != null) {
      mWindow.clear()
    } else {
      // 8 MB cursor window since we have large queries
      mWindow = CursorWindow(name, 8 * 1024 * 1024)
    }
  }
}
