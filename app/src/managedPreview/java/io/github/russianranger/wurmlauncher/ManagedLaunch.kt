package io.github.russianranger.wurmlauncher

import java.io.File

data class ManagedLaunch(val world: String, val heapMiB: Int = 4096, val port: Int = 3724) {
    fun validate(worlds: List<String>) {
        require(world in worlds && world.isNotBlank() && world !in listOf(".", "..") &&
            world.none { it == '/' || it == '\\' || it.isISOControl() }) { "Select an imported world." }
        require(heapMiB in 512..8192) { "Maximum heap must be 512–8192 MiB." }
        require(port in 1..65535) { "Expected TCP port must be 1–65535." }
    }

    fun arguments(native: File, home: File, tmp: File, runtime: File, helper: File, preflight: Boolean): List<String> {
        val cp = if (preflight) listOf(helper.absolutePath) + ProbeInputs.BASELINE.keys.map { File(runtime, "poc-lib/$it").absolutePath }
        else listOf("wurm-arm64-poc.jar") + ProbeInputs.BASELINE.keys.map { "poc-lib/$it" } +
            listOf("server.jar", "common.jar", "lib/*", helper.absolutePath)
        return listOf(File(native, "libwurmjvm_runner.so").absolutePath,
            if (preflight) "-Xms32m" else "-Xms512m", if (preflight) "-Xmx256m" else "-Xmx${heapMiB}m",
            "-Djava.awt.headless=true", "-Djava.home=${home.absolutePath}", "-Djava.io.tmpdir=${tmp.absolutePath}",
            "-Dorg.sqlite.tmpdir=${tmp.absolutePath}", "-Duser.home=${tmp.parentFile!!.absolutePath}",
            "-Djava.library.path=${home.absolutePath}/lib:${home.absolutePath}/lib/server:${native.absolutePath}",
            "-Dsun.boot.library.path=${home.absolutePath}/lib:${native.absolutePath}",
            "-XX:ErrorFile=${tmp.parentFile!!.absolutePath}/hs_err_pid%p.log", "-XX:-CreateCoredumpOnCrash") +
            (if (preflight) listOf("-Dwurm.probe.network=true") else emptyList()) + listOf(
            "-cp", cp.joinToString(":"), if (preflight) "probe.RuntimeProbe" else "server.ManagedServerMain",
            if (preflight) tmp.parentFile!!.absolutePath else world)
    }
}
