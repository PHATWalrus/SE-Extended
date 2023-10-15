package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.snapclient.MessagingBridge
import me.rhunk.snapenhance.bridge.snapclient.types.Message
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.snap.SnapWidgetBroadcastReceiverHelper
import me.rhunk.snapenhance.messaging.MessagingConstraints
import me.rhunk.snapenhance.messaging.MessagingTask
import me.rhunk.snapenhance.messaging.MessagingTaskConstraint
import me.rhunk.snapenhance.messaging.MessagingTaskType
import me.rhunk.snapenhance.ui.util.Dialog

class MessagingPreview(
    private val context: RemoteSideContext,
    private val scope: SocialScope,
    private val scopeId: String
) {
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var messagingBridge: MessagingBridge
    private lateinit var previewScrollState: LazyListState
    private val myUserId by lazy { messagingBridge.myUserId }

    private var conversationId: String? = null
    private val messages = sortedMapOf<Long, Message>() // server message id => message
    private var messageSize by mutableIntStateOf(0)
    private var lastMessageId = Long.MAX_VALUE
    private val selectedMessages = mutableStateListOf<Long>() // client message id

    private fun toggleSelectedMessage(messageId: Long) {
        if (selectedMessages.contains(messageId)) selectedMessages.remove(messageId)
        else selectedMessages.add(messageId)
    }

    @Composable
    private fun ActionButton(
        text: String,
        icon: ImageVector,
        onClick: () -> Unit,
    ) {
        DropdownMenuItem(
            onClick = onClick,
            text = {
                Row(
                    modifier = Modifier.padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                    Text(text = text)
                }
            }
        )
    }

    @Composable
    fun TopBarAction() {
        var showDropDown by remember { mutableStateOf(false) }
        var activeTask by remember { mutableStateOf(null as MessagingTask?) }
        var activeJob by remember { mutableStateOf(null as Job?) }
        val processMessageCount = remember { mutableIntStateOf(0) }

        fun triggerMessagingTask(taskType: MessagingTaskType, constraints: List<MessagingTaskConstraint> = listOf(), onSuccess: (Message) -> Unit = {}) {
            showDropDown = false
            processMessageCount.intValue = 0
            activeTask = MessagingTask(
                messagingBridge = messagingBridge,
                conversationId = conversationId!!,
                taskType = taskType,
                constraints = constraints,
                overrideClientMessageIds = selectedMessages.takeIf { it.isNotEmpty() }?.toList(),
                processedMessageCount = processMessageCount,
                onFailure = { message, reason ->
                    context.log.verbose("Failed to process message ${message.clientMessageId}: $reason")
                }
            )
            selectedMessages.clear()
            activeJob = coroutineScope.launch(Dispatchers.IO) {
                activeTask?.run()
                withContext(Dispatchers.Main) {
                    activeTask = null
                    activeJob = null
                }
            }.also { job ->
                job.invokeOnCompletion {
                    if (it != null) {
                        context.log.verbose("Failed to process messages: ${it.message}")
                        return@invokeOnCompletion
                    }
                    context.longToast("Processed ${processMessageCount.intValue} messages")
                }
            }
        }

        if (activeJob != null) {
            Dialog(onDismissRequest = {
                activeJob?.cancel()
                activeJob = null
                activeTask = null
            }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(20.dp))
                        .padding(15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Processed ${processMessageCount.intValue} messages")
                    if (activeTask?.hasFixedGoal() == true) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp),
                            progress = processMessageCount.intValue.toFloat() / selectedMessages.size.toFloat(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding()
                                .size(30.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        IconButton(onClick = { showDropDown = !showDropDown }) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
        }

        if (selectedMessages.isNotEmpty()) {
            IconButton(onClick = {
                selectedMessages.clear()
            }) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
            }
        }

        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.inverseSurface,
                onSurface = MaterialTheme.colorScheme.inverseOnSurface
            ),
            shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(50.dp))
        ) {
            DropdownMenu(
                expanded = showDropDown, onDismissRequest = {
                    showDropDown = false
                }
            ) {
                val hasSelection = selectedMessages.isNotEmpty()
                ActionButton(text = if (hasSelection) "Save selection" else "Save all", icon = Icons.Rounded.BookmarkAdded) {
                    triggerMessagingTask(MessagingTaskType.SAVE)
                }
                ActionButton(text = if (hasSelection) "Unsave selection" else "Unsave all", icon = Icons.Rounded.BookmarkBorder) {
                    triggerMessagingTask(MessagingTaskType.UNSAVE)
                }
                ActionButton(text = if (hasSelection) "Mark selected Snap as seen" else "Mark all Snaps as seen", icon = Icons.Rounded.RemoveRedEye) {
                    triggerMessagingTask(MessagingTaskType.READ, listOf(
                        MessagingConstraints.NO_USER_ID(myUserId),
                        MessagingConstraints.CONTENT_TYPE(ContentType.SNAP)
                    ))
                }
                ActionButton(text = if (hasSelection) "Delete selected" else "Delete all", icon = Icons.Rounded.DeleteForever) {
                    triggerMessagingTask(MessagingTaskType.DELETE, listOf(MessagingConstraints.USER_ID(myUserId))) { message ->
                        coroutineScope.launch {
                            messages.remove(message.serverMessageId)
                            messageSize = messages.size
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ConversationPreview() {
        DisposableEffect(Unit) {
            onDispose {
                selectedMessages.clear()
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = previewScrollState,
        ) {
            item {
                if (messages.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("No messages")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                LaunchedEffect(Unit) {
                    if (messages.size > 0) {
                        fetchNewMessages()
                    }
                }
            }
            items(messageSize) {index ->
                val elementKey = remember(index) { messages.entries.elementAt(index).value.clientMessageId }
                val messageReader = ProtoReader(messages.entries.elementAt(index).value.content)
                val contentType = ContentType.fromMessageContainer(messageReader)

                Card(
                    modifier = Modifier
                        .padding(5.dp)
                        .pointerInput(Unit) {
                            if (contentType == ContentType.STATUS) return@pointerInput
                            detectTapGestures(
                                onLongPress = {
                                    toggleSelectedMessage(elementKey)
                                },
                                onTap = {
                                    if (selectedMessages.isNotEmpty()) {
                                        toggleSelectedMessage(elementKey)
                                    }
                                }
                            )
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMessages.contains(elementKey)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(5.dp)
                    ) {

                        Text("[$contentType] ${messageReader.getString(2, 1) ?: ""}")
                    }
                }
            }
        }
    }

    private fun fetchNewMessages() {
        coroutineScope.launch(Dispatchers.IO) cs@{
            runCatching {
                val queriedMessages = messagingBridge.fetchConversationWithMessagesPaginated(
                    conversationId!!,
                    100,
                    lastMessageId
                )

                if (queriedMessages == null) {
                    context.shortToast("Failed to fetch messages")
                    return@cs
                }

                coroutineScope.launch {
                    messages.putAll(queriedMessages.map { it.serverMessageId to it })
                    messageSize = messages.size
                    if (queriedMessages.isNotEmpty()) {
                        lastMessageId = queriedMessages.first().clientMessageId
                        previewScrollState.scrollToItem(queriedMessages.size - 1)
                    }
                }
            }.onFailure {
                context.shortToast("Failed to fetch messages: ${it.message}")
            }
            context.log.verbose("fetched ${messages.size} messages")
        }
    }

    private fun onMessagingBridgeReady() {
        messagingBridge = context.bridgeService!!.messagingBridge!!
        conversationId = if (scope == SocialScope.FRIEND) messagingBridge.getOneToOneConversationId(scopeId) else scopeId
        if (conversationId == null) {
            context.longToast("Failed to fetch conversation id")
            return
        }
        fetchNewMessages()
    }

    @Composable
    private fun LoadingRow() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding()
                    .size(30.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    @Composable
    fun Content() {
        previewScrollState = rememberLazyListState()
        coroutineScope = rememberCoroutineScope()
        var isBridgeConnected by remember { mutableStateOf(false) }
        var hasBridgeError by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LaunchedEffect(Unit) {
                isBridgeConnected = context.hasMessagingBridge()
                if (isBridgeConnected) {
                    onMessagingBridgeReady()
                } else {
                    SnapWidgetBroadcastReceiverHelper.create("wakeup") {}.also {
                        context.androidContext.sendBroadcast(it)
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        withTimeout(10000) {
                            while (!context.hasMessagingBridge()) {
                                delay(100)
                            }
                            isBridgeConnected = true
                            onMessagingBridgeReady()
                        }
                    }.invokeOnCompletion {
                        if (it != null) {
                            hasBridgeError = true
                        }
                    }
                }
            }

            if (hasBridgeError) {
                Text("Failed to connect to Snapchat through bridge service")
            }

            if (!isBridgeConnected && !hasBridgeError) {
                LoadingRow()
            }

            if (isBridgeConnected && !hasBridgeError) {
                ConversationPreview()
            }
        }
    }
}