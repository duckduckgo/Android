/*
 * Copyright (c) 2022 DuckDuckGo
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

package dummy

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DataEntity(
    val id: Long,
    val title: String
)

data class DataModel(val title: String)

interface DataStore {
    fun getData(): DataModel
    fun storeData(data: DataModel)
}

class RealDataStore @Inject constructor(
    private val context: Context
) : DataStore {

    @VisibleForTesting
    val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_MULTI_PROCESS)

    override fun getData(): DataModel {
        return DataModel(preferences.getString(KEY_TITLE, "empty")!!)
    }

    override fun storeData(data: DataModel) {
        preferences.edit {
            putString(KEY_TITLE, data.title)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.mobile.prefs"
        private const val KEY_TITLE = "KEY_TITLE"
    }
}

@Dao
interface DataSuspendingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: DataEntity)

    @Query("select * from data_table")
    suspend fun getAll(): List<DataEntity>

    @Query("select * from data_table where id = :id")
    suspend fun getId(id: Long): DataEntity

    @Query("select * from data_table")
    suspend fun getFlow(): Flow<List<DataEntity>>
}

@Dao
interface DataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(data: DataEntity)

    @Query("select * from data_table")
    fun getAll(): List<DataEntity>

    @Query("select * from data_table where id = :id")
    fun getId(id: Long): DataEntity

    @Query("select * from data_table")
    fun getFlow(): Flow<List<DataEntity>>
}

interface Repository {

    suspend fun getAllFromDao(): List<DataEntity>

    suspend fun getIdFromDao(id: Long): DataEntity

    suspend fun getAllTransformed(): List<DataModel>

    fun getFromDataStore(): DataModel

    suspend fun getFlow(): Flow<List<DataEntity>>
}

class RealRepository @Inject constructor(
    private val dao: DataDao,
    private val suspendingDao: DataSuspendingDao,
    private val dataStore: DataStore,
    private val dispatcherProvider: DispatcherProvider
) : Repository {

    override suspend fun getAllFromDao(): List<DataEntity> {
        return dao.getAll()
    }

    override suspend fun getIdFromDao(id: Long): DataEntity {
        return dao.getId(id)
    }

    override suspend fun getAllTransformed(): List<DataModel> {
        return dao.getAll().map { dataEntity ->
            DataModel(dataEntity.title)
        }
    }

    override suspend fun getFlow(): Flow<List<DataEntity>> {
        return dao.getFlow()
            .flowOn(dispatcherProvider.io())
    }

    override fun getFromDataStore(): DataModel {
        return dataStore.getData()
    }
}

data class ViewState(val excludedApps: List<DataModel>)

class DataViewModel(
    private val repository: Repository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(emptyList()))

    val viewState: StateFlow<ViewState> = viewStateFlow

    internal suspend fun getDataFlow(): Flow<ViewState> = repository.getFlow().map { dataEntityList ->
        ViewState(
            dataEntityList.map { dataEntity ->
                DataModel(dataEntity.title)
            }
        )
    }

    internal suspend fun getFromDataStore() {
        viewModelScope.launch {
            val dataModel = withContext(dispatcherProvider.io()) {
                repository.getFromDataStore()
            }
            viewStateFlow.emit(ViewState(listOf(dataModel)))
        }
    }
}

class DataActivity() : DuckDuckGoActivity() {

    private val viewModel: DataViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }

        lifecycleScope.launch {
            viewModel.getDataFlow()
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .onEach {
                    render(it)
                }
        }
    }

    private fun render(viewState: ViewState) {
    }
}
