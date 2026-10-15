package com.xinglinghu.manager.data.repository

import com.xinglinghu.manager.data.model.Module
import com.xinglinghu.manager.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
