package com.pontoface.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ─── Entity ───────────────────────────────────────────────────────────────────

@Entity(tableName = "registros_ponto")
data class RegistroPonto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val funcionarioNome: String,
    val tipo: TipoPonto,           // ENTRADA / SAIDA / PAUSA / RETORNO
    val dataHora: String,          // ISO format
    val faceConfidence: Float,
    val fotoPath: String?,         // caminho local do snapshot
    val latitude: Double? = null,
    val longitude: Double? = null,
    val validado: Boolean = true
)

enum class TipoPonto(val label: String, val emoji: String) {
    ENTRADA("Entrada", "🟢"),
    SAIDA("Saída", "🔴"),
    PAUSA("Pausa", "🟡"),
    RETORNO("Retorno", "🔵")
}

// ─── Type Converters ──────────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromTipo(tipo: TipoPonto): String = tipo.name
    @TypeConverter fun toTipo(value: String): TipoPonto = TipoPonto.valueOf(value)
}

// ─── DAO ──────────────────────────────────────────────────────────────────────

@Dao
interface RegistroPontoDao {

    @Insert
    suspend fun inserir(registro: RegistroPonto): Long

    @Query("SELECT * FROM registros_ponto ORDER BY dataHora DESC")
    fun getAllFlow(): Flow<List<RegistroPonto>>

    @Query("SELECT * FROM registros_ponto ORDER BY dataHora DESC LIMIT 50")
    suspend fun getRecentes(): List<RegistroPonto>

    @Query("SELECT * FROM registros_ponto WHERE funcionarioNome = :nome ORDER BY dataHora DESC")
    fun getByFuncionario(nome: String): Flow<List<RegistroPonto>>

    @Query("SELECT * FROM registros_ponto WHERE dataHora LIKE :data || '%' ORDER BY dataHora ASC")
    suspend fun getByData(data: String): List<RegistroPonto>

    @Query("SELECT COUNT(*) FROM registros_ponto")
    suspend fun count(): Int

    @Delete
    suspend fun deletar(registro: RegistroPonto)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [RegistroPonto::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PontoDatabase : RoomDatabase() {
    abstract fun registroDao(): RegistroPontoDao
}

// ─── Repository ───────────────────────────────────────────────────────────────

class PontoRepository(private val dao: RegistroPontoDao) {

    val todosRegistros = dao.getAllFlow()

    suspend fun registrarPonto(
        nome: String,
        tipo: TipoPonto,
        confidence: Float,
        fotoPath: String?
    ): Long {
        val agora = LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val registro = RegistroPonto(
            funcionarioNome = nome,
            tipo = tipo,
            dataHora = agora,
            faceConfidence = confidence,
            fotoPath = fotoPath,
            validado = confidence >= 0.65f
        )
        return dao.inserir(registro)
    }

    suspend fun getRecentes() = dao.getRecentes()
}
