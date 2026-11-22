package com.grayman.englishstudy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.grayman.englishstudy.app
import com.grayman.englishstudy.data.ProgressSync
import com.grayman.englishstudy.data.words.GoogleAccount
import com.grayman.englishstudy.data.words.SyncStatus
import com.grayman.englishstudy.ui.ChunkyButton
import com.grayman.englishstudy.ui.Game
import com.grayman.englishstudy.ui.SecondaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Settings card in the records tab: one button that syncs everything with the other device
 * (study records here; the word list also syncs on its own), and the Google account connection.
 */
@Composable
fun SyncAllCard() {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val account by app.googleAccount.email.collectAsState()
    val wordStatus by app.wordSync.status.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val progressStatus by app.progressSync.status.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("기기 동기화", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "레슨, 리스닝, 역할극, 단어 외우기 기록이 끝날 때마다 자동으로 서버에 저장되고, 앱을 켤 때 다른 기기 기록을 받아 와요. 버튼은 지금 바로 맞추고 싶을 때 눌러요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when {
                account != null -> "구글 계정: $account"
                else -> "구글 계정에 연결하지 않았어요. 앱을 지우면 이 기기의 연결이 끊겨요."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (account != null) Game.OnGoodSoft else Game.LagoonEdge,
        )
        Text(
            when (val st = progressStatus) {
                ProgressSync.Status.Idle -> "아직 서버에 저장한 적이 없어요"
                ProgressSync.Status.Syncing -> "서버와 맞추는 중…"
                is ProgressSync.Status.Done -> "마지막 저장: " + SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA).format(Date(st.atMillis))
                is ProgressSync.Status.Failed ->
                    if (st.permissionDenied) "서버가 허용하지 않았어요. Firestore 규칙을 확인해 주세요."
                    else "저장하지 못했어요: ${st.message} (인터넷이 연결되면 다음 학습 때 다시 시도해요)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (progressStatus is ProgressSync.Status.Failed) Game.Bad else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (account == null) {
                SecondaryButton(
                    text = "구글 계정으로 연결",
                    onClick = {
                        if (busy) return@SecondaryButton
                        busy = true
                        message = null
                        scope.launch {
                            message = when (val r = app.googleAccount.connect(context)) {
                                GoogleAccount.Result.Linked -> "구글 계정에 연결했어요. 이 기기의 UID는 그대로예요."
                                GoogleAccount.Result.Switched -> "구글 계정에 연결했어요. 먼저 연결한 기기와 같은 UID를 써요."
                                GoogleAccount.Result.Cancelled -> null
                                is GoogleAccount.Result.Failed -> "연결하지 못했어요: ${r.message}"
                            }
                            app.wordSync.start()
                            busy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            ChunkyButton(
                text = if (busy) "동기화하는 중…" else "전체 동기화",
                icon = Icons.Rounded.CloudSync,
                enabled = !busy,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        message = try {
                            if (wordStatus !is SyncStatus.Connected) app.wordSync.start()
                            val s = app.progressSync.syncAll()
                            "맞췄어요. 끝낸 레슨 ${s.lessons}개, 공부한 날 ${s.days}일, 복습 표현 ${s.cards}개, 리스닝 ${s.listening}회, 총 ${s.totalXp} XP"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: FirebaseFirestoreException) {
                            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                "서버가 허용하지 않았어요. Firestore 규칙에 학습 기록(progress) 칸이 있는지, 이 기기가 허용됐는지 확인해 주세요."
                            } else {
                                "동기화하지 못했어요: ${e.message}"
                            }
                        } catch (e: Exception) {
                            "동기화하지 못했어요: ${e.message ?: e.javaClass.simpleName}"
                        }
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
    }
}
