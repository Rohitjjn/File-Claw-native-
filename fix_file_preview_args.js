const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const oldSig = `fun FilePreviewScreen(
    fileEntity: RecentFileEntity,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current`;

const newSig = `fun FilePreviewScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentState by viewModel.currentFileState.androidx.compose.runtime.collectAsState()
    
    val fileEntity = when (currentState) {
        is com.example.viewmodel.MainViewModel.FileContentState.TextSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.TextSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.CsvSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.CsvSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.ExcelSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.ExcelSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.ZipSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.ZipSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.ImageSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.ImageSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.PdfSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.PdfSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.DocxSuccess -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.DocxSuccess).file
        is com.example.viewmodel.MainViewModel.FileContentState.ArchivePasswordRequired -> (currentState as com.example.viewmodel.MainViewModel.FileContentState.ArchivePasswordRequired).file
        else -> null
    }

    if (fileEntity == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No file selected.")
        }
        return
    }
`;

content = content.replace(oldSig, newSig);

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
