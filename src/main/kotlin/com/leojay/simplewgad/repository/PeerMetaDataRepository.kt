package com.leojay.simplewgad.repository

import com.leojay.simplewgad.model.ClientMetaData
import com.leojay.simplewgad.model.PeerMetaData
import com.leojay.simplewgad.model.ServerMetaData
import com.leojay.simplewgad.util.ShellCommandExecutor
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.util.UUID

//todo :考虑加个锁
@Component
class PeerMetaDataRepository(
    private val objectMapper: ObjectMapper,
    private val commandExecutor: ShellCommandExecutor,
    @Value("\${wg-manager.peer-meta-data.path}") val dataPath: String,
    @Value("\${wg-manager.default.interface-name}") val defaultInterfaceName: String,
    @Value("\${wg-manager.default.server-private-key}") val serverPrivateKey: String,
) {


    private val dataFile by lazy {
        File("$dataPath$defaultInterfaceName.json").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private lateinit var peerMetaData: PeerMetaData

    fun getMaskedData() = peerMetaData.copy(
        server = peerMetaData.server.copy(privateKey = "******"),
        clients = peerMetaData.clients.mapValues { it.value.copy(privateKey = "******") }
    )
    fun getUnmaskedData() = peerMetaData.copy()

    fun addClientMetaData(client: ClientMetaData) {
        peerMetaData.clients += UUID.randomUUID().toString() to client
        saveToFile()
    }

    fun deleteClientMetaData(clientUuid: String): Boolean {
        return if (peerMetaData.clients.containsKey(clientUuid)) {
            peerMetaData.clients -= clientUuid
            saveToFile()
            true
        } else {
            false
        }
    }

    private fun loadFromFile() = objectMapper.readValue(dataFile.readText(), PeerMetaData::class.java).also {
        logger.info("Loaded peer meta data from file ${dataFile.absolutePath}")
    }

    private fun saveToFile() {
        try {
            dataFile.writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(peerMetaData))
        } catch (e: Exception) {
            throw e.also {
                logger.error("Failed to save client metadata to file: ${e.message}", e)
            }
        }
    }

    /**
     * 初始化元数据文件
     * 如果没有这创建文件
     * 如果为空这使用spring配置中的私钥和地址写入
     */
    @PostConstruct
    fun initMetaDataFile() {
        try {
            peerMetaData = objectMapper.readValue(dataFile, PeerMetaData::class.java)
        } catch (e: Exception) {
            logger.error("Failed to load peer meta data from file ${dataFile.absolutePath}", e)
            //读取失败的话用默认值填充

            peerMetaData = PeerMetaData(
                server = ServerMetaData(
                    privateKey = serverPrivateKey,
                    address = "10.0.7.1/24",
                    publicKey = getPublicKey()
                ),
                clients = mapOf()
            ).also {
                logger.error("Failed to load peer meta data from file ${dataFile.absolutePath}", e)
            }
            saveToFile()
        }
    }

    private fun getPublicKey() = commandExecutor.runCommand("echo $serverPrivateKey | sudo wg pubkey").let {
        if (it.exitCode != 0) throw RuntimeException("生成公钥失败: ${it.errMsg}")
        it.output.trim()
    }


}
