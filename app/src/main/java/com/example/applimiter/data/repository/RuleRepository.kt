package com.example.applimiter.data.repository

import com.example.applimiter.data.db.RuleDao
import com.example.applimiter.data.db.RuleEntity
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val ruleDao: RuleDao) {

    val allRules: Flow<List<RuleEntity>> = ruleDao.getAllRules()
    val enabledRules: Flow<List<RuleEntity>> = ruleDao.getEnabledRules()

    suspend fun getEnabledRulesNow(): List<RuleEntity> = ruleDao.getEnabledRulesNow()
    suspend fun allRulesNow(): List<RuleEntity> = ruleDao.getAllRulesNow()

    suspend fun getById(id: Long): RuleEntity? = ruleDao.getById(id)

    suspend fun insert(rule: RuleEntity): Long = ruleDao.insert(rule)
    suspend fun insertAll(rules: List<RuleEntity>): List<Long> = ruleDao.insertAll(rules)
    suspend fun update(rule: RuleEntity) = ruleDao.update(rule)
    suspend fun delete(rule: RuleEntity) = ruleDao.delete(rule)
    suspend fun deleteByPackage(pkg: String) = ruleDao.deleteByPackage(pkg)
    suspend fun setEnabled(id: Long, enabled: Boolean) = ruleDao.setEnabled(id, enabled)
}
