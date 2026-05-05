package production.d1tan.d1tnotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────── ROOM: сущность, DAO, база ───────────

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val isPinned: Boolean = false
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Insert
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}

@Database(
    entities = [Note::class],
    version = 2,
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

// ─────────── простая навигация ───────────

enum class Screen {
    LIST,
    CREATE,
    EDIT
}

enum class SortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotesApp()
        }
    }
}

// ─────────── общий верхний бар ───────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    appName: String,
    versionName: String
) {
    CenterAlignedTopAppBar(
        title = {
            val versionLabel = if (versionName.isNotBlank()) "v$versionName" else ""
            Text("$appName $versionLabel".trim())
        }
    )
}

// ─────────── корневой composable ───────────

@Composable
fun NotesApp() {
    val context = LocalContext.current

    // название приложения и версия
    val appName = remember { context.getString(R.string.app_name) }
    val versionName = remember {
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // БД Room
    val db = remember {
        Room.databaseBuilder(
            context,
            NoteDatabase::class.java,
            "notes.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    val noteDao = db.noteDao()

    // Состояние UI
    val notes = remember { mutableStateListOf<Note>() }
    var currentScreen by remember { mutableStateOf(Screen.LIST) }
    var noteBeingEdited by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }

    val scope = rememberCoroutineScope()

    // для undo
    val snackbarHostState = remember { SnackbarHostState() }
    var recentlyDeletedNote by remember { mutableStateOf<Note?>(null) }

    suspend fun loadNotes() {
        val fromDb = noteDao.getAll()
        notes.clear()
        notes.addAll(fromDb)
    }

    // начальная загрузка
    LaunchedEffect(Unit) {
        loadNotes()
    }

    // каждый раз, когда возвращаемся на список — обновляем из БД
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.LIST) {
            loadNotes()
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            when (currentScreen) {
                Screen.LIST -> {
                    NotesListScreen(
                        appName = appName,
                        versionName = versionName,
                        notes = notes,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        sortOrder = sortOrder,
                        onSortOrderChange = { sortOrder = it },
                        onAddClick = { currentScreen = Screen.CREATE },
                        onNoteClick = { note ->
                            noteBeingEdited = note
                            currentScreen = Screen.EDIT
                        },
                        onTogglePin = { note ->
                            scope.launch {
                                noteDao.update(note.copy(isPinned = !note.isPinned))
                                loadNotes()
                            }
                        },
                        snackbarHostState = snackbarHostState
                    )
                }

                Screen.CREATE -> {
                    CreateNoteScreen(
                        appName = appName,
                        versionName = versionName,
                        onSave = { title, content ->
                            scope.launch {
                                if (title.isNotBlank() || content.isNotBlank()) {
                                    noteDao.insert(
                                        Note(
                                            title = title.trim(),
                                            content = content.trim()
                                        )
                                    )
                                }
                                currentScreen = Screen.LIST
                            }
                        },
                        onCancel = {
                            currentScreen = Screen.LIST
                        }
                    )
                }

                Screen.EDIT -> {
                    val note = noteBeingEdited
                    if (note == null) {
                        currentScreen = Screen.LIST
                    } else {
                        EditNoteScreen(
                            appName = appName,
                            versionName = versionName,
                            note = note,
                            onAutoSave = { title, content ->
                                scope.launch {
                                    // авто-сохранение без trim, чтобы не резать пробелы во время набора
                                    noteDao.update(
                                        note.copy(
                                            title = title,
                                            content = content
                                        )
                                    )
                                }
                            },
                            onSave = { title, content ->
                                scope.launch {
                                    noteDao.update(
                                        note.copy(
                                            title = title.trim(),
                                            content = content.trim()
                                        )
                                    )
                                    currentScreen = Screen.LIST
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    recentlyDeletedNote = note
                                    noteDao.delete(note)
                                    currentScreen = Screen.LIST

                                    val result = snackbarHostState.showSnackbar(
                                        message = "Заметка удалена",
                                        actionLabel = "Отменить"
                                    )

                                    if (result == SnackbarResult.ActionPerformed) {
                                        recentlyDeletedNote?.let { deleted ->
                                            noteDao.insert(
                                                deleted.copy(id = 0)
                                            )
                                        }
                                    }
                                    recentlyDeletedNote = null
                                }
                            },
                            onCancel = {
                                currentScreen = Screen.LIST
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────── экран списка + поиск ───────────

@Composable
fun NotesListScreen(
    appName: String,
    versionName: String,
    notes: List<Note>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onAddClick: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        topBar = {
            AppTopBar(appName, versionName)
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить заметку"
                )
            }
        }
    ) { padding ->
        val sortedNotes =
            when (sortOrder) {
                SortOrder.NEWEST_FIRST ->
                    notes.sortedWith(
                        compareByDescending<Note> { it.isPinned }
                            .thenByDescending { it.id }
                    )

                SortOrder.OLDEST_FIRST ->
                    notes.sortedWith(
                        compareByDescending<Note> { it.isPinned }
                            .thenBy { it.id }
                    )
            }

        val filteredNotes =
            if (searchQuery.isBlank()) {
                sortedNotes
            } else {
                val q = searchQuery.trim()
                sortedNotes.filter { note ->
                    note.title.contains(q, ignoreCase = true) ||
                            note.content.contains(q, ignoreCase = true)
                }
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск заметок") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск"
                    )
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Переключатель сортировки
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onSortOrderChange(SortOrder.NEWEST_FIRST) }) {
                    Text(
                        text = "Сначала новые",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (sortOrder == SortOrder.NEWEST_FIRST)
                            androidx.compose.ui.text.font.FontWeight.SemiBold
                        else
                            androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
                TextButton(onClick = { onSortOrderChange(SortOrder.OLDEST_FIRST) }) {
                    Text(
                        text = "Сначала старые",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (sortOrder == SortOrder.OLDEST_FIRST)
                            androidx.compose.ui.text.font.FontWeight.SemiBold
                        else
                            androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                notes.isEmpty() -> {
                    EmptyNotesState()
                }

                filteredNotes.isEmpty() -> {
                    NoResultsState()
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredNotes) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onTogglePin = { onTogglePin(note) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNotesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Пока нет ни одной заметки",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Нажми на +, чтобы добавить первую ✨",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun NoResultsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ничего не найдено",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Попробуй изменить запрос поиска",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        onTogglePin()
                    }
                ) {
                    Icon(
                        imageVector = if (note.isPinned) {
                            Icons.Filled.PushPin
                        } else {
                            Icons.Outlined.PushPin
                        },
                        contentDescription = if (note.isPinned) {
                            "Открепить"
                        } else {
                            "Закрепить"
                        }
                    )
                }
            }

            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────── экран создания ───────────

@Composable
fun CreateNoteScreen(
    appName: String,
    versionName: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            AppTopBar(appName, versionName)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Новая заметка",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Текст заметки") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                maxLines = Int.MAX_VALUE
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Button(onClick = { onCancel() }) {
                    Text("Отмена")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onSave(title, content) }) {
                    Text("Сохранить")
                }
            }
        }
    }
}

// ─────────── экран редактирования ───────────

@Composable
fun EditNoteScreen(
    appName: String,
    versionName: String,
    note: Note,
    onAutoSave: (String, String) -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    // авто-сохранение с дебаунсом
    val scope = rememberCoroutineScope()
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(600) // мс — можно подправить
            onAutoSave(title, content)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(appName, versionName)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Редактировать заметку",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    scheduleAutoSave()
                },
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    scheduleAutoSave()
                },
                label = { Text("Текст заметки") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                maxLines = Int.MAX_VALUE
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { showDeleteDialog = true }) {
                    Text("Удалить")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Button(onClick = { onCancel() }) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onSave(title, content) }) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("Удалить заметку?")
            },
            text = {
                Text("Вы точно хотите удалить эту заметку?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ─────────── превью (по желанию) ───────────

@Preview(showBackground = true)
@Composable
fun CreateNotePreview() {
    MaterialTheme {
        CreateNoteScreen(
            appName = "D1tNotes",
            versionName = "1.0",
            onSave = { _, _ -> },
            onCancel = {}
        )
    }
}