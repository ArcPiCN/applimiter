package com.example.applimiter.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE isEnabled = 1")
    fun getEnabledRules(): Flow<List<RuleEntity>>

    /** 同步获取所有启用规则，供无障碍服务在事件回调里使用。 */
    @Query("SELECT * FROM rules WHERE isEnabled = 1")
    suspend fun getEnabledRulesNow(): List<RuleEntity>

    /** 同步获取全部规则，供导出使用。 */
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    suspend fun getAllRulesNow(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): RuleEntity?

    @Insert
    suspend fun insert(rule: RuleEntity): Long

    @Insert
    suspend fun insertAll(rules: List<RuleEntity>): List<Long>

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("UPDATE rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
