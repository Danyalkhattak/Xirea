package com.dannyk.xirea.data.repository

import com.dannyk.xirea.data.dao.ChatDao
import com.dannyk.xirea.data.dao.MessageDao
import com.dannyk.xirea.data.model.Chat
import com.dannyk.xirea.data.model.Message
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {
    val allChats: Flow<List<Chat>> = chatDao.getAllChats()
    val chatCount: Flow<Int> = chatDao.getChatCount()
    
    fun getMessagesForChat(chatId: Long): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId)
    }
    
    suspend fun getChatById(chatId: Long): Chat? {
        return chatDao.getChatById(chatId)
    }
    
    suspend fun createChat(title: String): Long {
        val chat = Chat(title = title)
        return chatDao.insertChat(chat)
    }
    
    suspend fun updateChat(chat: Chat) {
        chatDao.updateChat(chat)
    }
    
    suspend fun deleteChat(chat: Chat) {
        chatDao.deleteChat(chat)
    }
    
    suspend fun deleteChatById(chatId: Long) {
        chatDao.deleteChatById(chatId)
    }
    
    suspend fun deleteAllChats() {
        chatDao.deleteAllChats()
    }
    
    suspend fun addMessage(chatId: Long, content: String, isFromUser: Boolean): Long {
        val message = Message(
            chatId = chatId,
            content = content,
            isFromUser = isFromUser
        )
        val messageId = messageDao.insertMessage(message)
        
        // Update chat's updatedAt timestamp
        val chat = chatDao.getChatById(chatId)
        chat?.let {
            chatDao.updateChat(it.copy(updatedAt = System.currentTimeMillis()))
        }
        
        return messageId
    }
    
    suspend fun updateMessage(message: Message) {
        messageDao.updateMessage(message)
    }
    
    suspend fun getLastMessageForChat(chatId: Long): Message? {
        return messageDao.getLastMessageForChat(chatId)
    }
}
