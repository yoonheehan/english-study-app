package com.grayman.englishstudy.ui.words

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.grayman.englishstudy.data.words.SyncStatus
import com.grayman.englishstudy.ui.Game

/** The cloud button's icon and color for each sync state. */
@Composable
fun syncIcon(status: SyncStatus): Pair<ImageVector, Color> = when (status) {
    is SyncStatus.Connected -> Icons.Rounded.CloudDone to Game.Good
    is SyncStatus.NotAllowed, is SyncStatus.Failed -> Icons.Rounded.CloudOff to MaterialTheme.colorScheme.error
    SyncStatus.Starting, is SyncStatus.Connecting -> Icons.Rounded.CloudSync to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun SyncDialog(
    status: SyncStatus,
    pending: Int,
    contentInfo: String?,
    uploadProblem: String? = null,
    account: String?,
    connecting: Boolean,
    accountMessage: String?,
    onConnectGoogle: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uid = when (status) {
        is SyncStatus.Connected -> status.uid
        is SyncStatus.Connecting -> status.uid
        is SyncStatus.NotAllowed -> status.uid
        is SyncStatus.Failed -> status.uid
        SyncStatus.Starting -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
        title = { Text("기기 동기화") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(describe(status), style = MaterialTheme.typography.bodyLarge)
                if (pending > 0) {
                    Text(
                        "아직 올리지 못한 변경 ${pending}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uploadProblem?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                contentInfo?.let {
                    Text(
                        "리스닝·스피킹 콘텐츠: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AccountCard(account, connecting, accountMessage, onConnectGoogle)
                if (uid != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Text("이 기기의 UID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectionContainer(Modifier.weight(1f)) {
                                Text(uid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { copy(context, uid) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "UID 복사")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text("다시 연결") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

private fun describe(status: SyncStatus): String = when (status) {
    SyncStatus.Starting -> "로그인하는 중이에요."
    is SyncStatus.Connecting -> "서버에 연결하는 중이에요. 인터넷이 꺼져 있으면 변경은 기기에 모아 뒀다가 연결되면 올려요."
    is SyncStatus.Connected -> "연결됐어요. 다른 기기와 단어장이 자동으로 맞춰져요."
    is SyncStatus.NotAllowed ->
        "이 기기는 아직 허용되지 않았어요. 다른 기기에서 쓰는 구글 계정으로 연결하거나, 아래 UID를 Firestore 규칙에 넣고 게시한 뒤 '다시 연결'을 눌러 주세요."
    is SyncStatus.Failed -> "연결하지 못했어요: ${status.message}"
}

/** Google account status: connected, or a button to connect. */
@Composable
private fun AccountCard(account: String?, connecting: Boolean, message: String?, onConnect: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (account != null) Game.GoodSoft else Game.LagoonSoft, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (account != null) {
            Text("구글 계정: $account", style = MaterialTheme.typography.titleSmall, color = Game.OnGoodSoft)
            Text(
                "앱을 지웠다 다시 깔거나 기기를 바꿔도 이 계정으로 로그인하면 그대로 이어져요.",
                style = MaterialTheme.typography.bodySmall,
                color = Game.OnGoodSoft,
            )
        } else {
            Text(
                "지금은 이 기기만의 임시 계정이에요. 앱을 지우면 UID가 바뀌어요. 폰과 태블릿에서 같은 구글 계정으로 연결해 두세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Game.LagoonEdge,
            )
            TextButton(onClick = onConnect, enabled = !connecting) {
                Text(if (connecting) "연결하는 중…" else "구글 계정으로 연결")
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("UID", text))
    Toast.makeText(context, "UID를 복사했어요", Toast.LENGTH_SHORT).show()
}
