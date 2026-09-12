package io.github.russianranger.wurmlauncher

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

/** Caller holds the side's session ownership and native process/file lock. Lives inside its runtime,
 * so server checkpoints/export/restore include the manifest, disabled files and mod-created databases. */
class ModStore(val runtime: File, val side: String, private val moved: (Int) -> Unit = {}) {
    data class Entry(val name: String, val side: String, val version: String, val enabled: Boolean,
        val files: Map<String,String>, val properties: Map<String,String>)
    private val home = File(runtime,"android-mods")
    private val manifest = File(home,"manifest.properties")
    private val journal = File(home,"transaction.properties")
    val loader = File(home,"loader/modlauncher.jar")
    init { require(side in listOf("server","client")) }

    fun entries(): List<Entry> = decode(readProperties(manifest))
    fun loaderInstalled() = loader.isFile && sha(loader)==SERVER_LOADER_SHA
    fun recover() {
        if (!journal.isFile) return
        val p=readProperties(journal)
        val next=Properties().apply { load(Base64.getDecoder().decode(p.getProperty("manifest")).inputStream()) }
        decode(next) // Validate before completing any movement.
        val count=p.getProperty("moves").toInt().also { require(it in 0..512) }
        repeat(count) { index ->
            val from=ownedPath(p.getProperty("$index.from")); val to=ownedPath(p.getProperty("$index.to"))
            check(from.exists() xor to.exists()) { "Ambiguous interrupted mod move. Keep the working export; inspect ${from.name}." }
            if (from.exists()) { to.parentFile!!.mkdirs(); Files.move(from.toPath(),to.toPath(),ATOMIC_MOVE) }
        }
        atomic(manifest,encodeProperties(next)); check(journal.delete())
        home.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("import-") }.forEach { it.deleteRecursively() }
    }

    /** Supports an Ago mods/ directory, its contents, or one enclosing release folder. */
    fun importZip(input: InputStream): String {
        recover(); home.mkdirs()
        val stage=File(home,"import-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            unzip(input,stage)
            var root=stage
            repeat(3) {
                val list=root.listFiles().orEmpty().filterNot { documentation(it.name) }
                if (File(root,"modlauncher.jar").isFile || list.any { it.isFile && it.name.endsWith(".properties") }) return@repeat
                root=when {
                    File(root,"mods").isDirectory -> File(root,"mods")
                    list.size==1 && list[0].isDirectory -> list[0]
                    else -> root
                }
            }
            if (File(root,"modlauncher.jar").isFile) {
                require(side=="server") { "Client loader installation is deferred until server testing passes." }
                val jar=File(root,"modlauncher.jar")
                require(sha(jar)==SERVER_LOADER_SHA) { "Use Ago's server-modlauncher-0.47.zip for this test." }
                loader.parentFile!!.mkdirs()
                atomic(loader,jar.readBytes())
                return "Server loader 0.47 installed. Optional bundled mods were not activated. Import individual mod ZIPs next."
            }
            val before=entries()
            val descriptors=root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".properties") }
            require(descriptors.isNotEmpty()) { "No mod descriptors found. Import an Ago mod ZIP containing mods/name.properties and mods/name/name.jar." }
            require(before.size+descriptors.size<=100) { "Maximum 100 imported mods per side." }
            val imported=descriptors.sortedBy { it.name }.map { descriptor ->
                val name=descriptor.name.removeSuffix(".properties"); validName(name)
                require(before.none { it.name.equals(name,true) }) { "$name is already imported. Existing configuration was preserved." }
                require(!File(runtime,"mods/$name").exists() && !File(runtime,"mods/$name.properties").exists() && !File(runtime,"mods/$name.config").exists()) { "Unmanaged files already occupy $name. Use a clean runtime or export them first." }
                val props=effective(root,name)
                val classname=props.getProperty("classname") ?: error("$name: missing classname")
                require(classname.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*"))) { "$name: invalid classname" }
                val cp=props.getProperty("classpath", "$name.jar").split(',').map { it.trim().also(::validPath) }
                val jars=cp.map { File(root,"$name/$it") }
                require(jars.all { it.isFile && it.extension=="jar" }) { "$name: missing classpath JAR. Keep its complete release ZIP." }
                var main: ByteArray?=null; var version="unknown"
                jars.forEach { file -> JarFile(file).use { jar ->
                    jar.getJarEntry(classname.replace('.','/')+".class")?.let { cls ->
                        check(main==null) { "$name: duplicate entry class" }; main=jar.getInputStream(cls).use { it.readBytes() }
                        version=jar.manifest?.mainAttributes?.getValue("Implementation-Version") ?: "unknown"
                    }
                } }
                val bytecode=requireNotNull(main) { "$name: entry class not found" }
                val text=bytecode.toString(Charsets.ISO_8859_1)
                val opposite=if(side=="server") "WurmClientMod" else "WurmServerMod"
                require(!text.contains(opposite)) { "$name belongs to the other side. Use its ${if(side=="server") "Client" else "Server"} import button." }
                require(text.contains(if(side=="server") "WurmServerMod" else "WurmClientMod")) { "$name uses an unrecognized/legacy mod interface; needs compatibility review." }
                require(!props.getProperty("sharedClassLoader","false").toBoolean()) { "$name requires a shared classloader; deferred for this initial test." }
                val paths=units(name).map { File(root,it) }.filter { it.exists() }
                val files=inventory(root,paths)
                require(files.keys.none { it.endsWith(".dll",true)||it.endsWith(".so",true)||it.endsWith(".exe",true) }) { "$name includes desktop/native code; needs an Android compatibility review." }
                Entry(name,side,version,false,files,props.stringPropertyNames().associateWith { props.getProperty(it) })
            }
            val names=imported.map { it.name }.toSet()
            require(root.listFiles().orEmpty().all { documentation(it.name) || it.name in names || it.name.removeSuffix(".properties").removeSuffix(".config") in names }) { "ZIP contains files outside its mod directories. No runtime files were replaced." }
            val moves=imported.flatMap { e -> units(e.name).mapNotNull { unit ->
                File(root,unit).takeIf { it.exists() }?.let { it to File(home,"disabled/${e.name}/$unit") }
            } }
            transition(before+imported,moves)
            return "Imported ${imported.joinToString { it.name }} disabled. Enable each mod when ready to test."
        } finally { if (!journal.exists()) stage.deleteRecursively() }
    }

    fun toggle(name: String, enabled: Boolean) {
        recover(); val before=entries(); val e=before.single { it.name==name }
        if(e.enabled==enabled) return
        val source=if(e.enabled) File(runtime,"mods") else File(home,"disabled/$name")
        val target=if(enabled) File(runtime,"mods") else File(home,"disabled/$name")
        checkJars(e,source)
        val paths=units(name).map { File(source,it) }.filter { it.exists() }
        // Capture files created within the owned folder/config by the running mod before disabling it.
        val updated=e.copy(enabled=enabled,files=inventory(source,paths))
        val next=before.map { if(it.name==name) updated else it }
        validateDependencies(next)
        transition(next,paths.map { it to File(target,it.relativeTo(source).invariantSeparatorsPath) })
    }

    fun validate(): List<Entry> {
        recover(); val all=entries()
        all.forEach { e -> checkJars(e,if(e.enabled) File(runtime,"mods") else File(home,"disabled/${e.name}")) }
        val owned=all.filter { it.enabled }.flatMap { units(it.name) }.toSet()
        val unknown=File(runtime,"mods").listFiles().orEmpty().filter { it.name !in owned && (it.isFile || it.walkTopDown().any { f->f.isFile }) }
        check(unknown.isEmpty()) { "Unmanaged active mod files: ${unknown.joinToString { it.name }}. Import through Mods before launching." }
        validateDependencies(all)
        if(side=="server" && all.any { it.enabled }) check(loaderInstalled()) { "Import Ago's server loader 0.47 ZIP in Mods first." }
        return all
    }

    fun report(): String = buildString {
        appendLine("MOD_MANIFEST side=$side runtime=$runtime")
        appendLine("Server loader installed=${side=="server" && loaderInstalled()}; client execution deferred=${side=="client"}")
        appendLine("Pending transaction=${journal.exists()}")
        entries().forEach { e ->
            appendLine("${e.name} version=${e.version} enabled=${e.enabled} destination=${if(e.enabled) "mods" else "android-mods/disabled/${e.name}"}")
            e.files.forEach { (path,hash)->appendLine("  $hash $path") }
        }
    }

    private fun effective(root: File, name: String): Properties {
        val p=Properties(); val jar=File(root,"$name/$name.jar")
        var template: ByteArray?=null
        if(jar.isFile) JarFile(jar).use { j ->
            val base="META-INF/org.gotti.wurmunlimited.modloader/$name"
            j.getJarEntry("$base.properties")?.let { j.getInputStream(it).use { input -> p.load(input) } }
            j.getJarEntry("$base.config")?.let { template=j.getInputStream(it).use { input -> input.readBytes() } }
        }
        p.putAll(readProperties(File(root,"$name.properties")))
        val config=File(root,"$name.config")
        if(config.isFile) p.putAll(readProperties(config))
        else template?.inputStream()?.use { p.load(it) }
        return p
    }
    private fun structural(p: Map<String,String>)=p.filterKeys {
        it=="classname" || it=="classpath" || it=="sharedClassLoader" || it.startsWith("depend.")
    }
    private fun checkJars(e: Entry, root: File) {
        e.files.filterKeys { it.endsWith(".jar",true)||it=="${e.name}.properties" }.forEach { (path,hash) ->
            check(File(root,path).isFile && sha(File(root,path))==hash) { "${e.name}: imported code/descriptor changed at $path. Restore its matching export." }
        }
        val effective=effective(root,e.name)
        check(structural(e.properties)==structural(effective.stringPropertyNames().associateWith { effective.getProperty(it) })) {
            "${e.name}: classpath/dependency configuration changed; restore its matching export before launch."
        }
        val extra=File(root,e.name).walkTopDown().filter { it.isFile && it.extension.equals("jar",true) && it.relativeTo(root).invariantSeparatorsPath !in e.files }.toList()
        check(extra.isEmpty()) { "${e.name}: untracked JARs need review before launch." }
    }
    private fun validateDependencies(all: List<Entry>) {
        val enabled=all.filter { it.enabled }.associateBy { it.name }
        enabled.values.forEach { e ->
            val required=listOf("depend.requires","depend.import").flatMap { e.properties[it].orEmpty().split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
            required.forEach { dependency ->
                val name=dependency.substringBefore('@')
                check(name=="modloader" || name in enabled) { "${e.name} requires $dependency. Enable its dependency first (or disable dependent mods first)." }
                // The upstream resolver validates exact version expressions during startup.
            }
            e.properties["depend.conflicts"].orEmpty().split(',').map { it.trim().substringBefore('@') }.filter { it.isNotEmpty() }.forEach {
                check(it !in enabled) { "${e.name} conflicts with $it. Disable one first." }
            }
            check(!e.properties.values.any { it.contains("scriptrunner",true) } && e.name!="scriptrunner") { "ScriptRunner needs a Java 17 script engine; it is not supported in this test." }
        }
    }
    private fun transition(next: List<Entry>, moves: List<Pair<File,File>>) {
        moves.forEach { (from,to)->check(from.exists() && !to.exists()) { "Mod destination already occupied: ${to.name}" } }
        home.mkdirs()
        val p=Properties().apply {
            setProperty("manifest",Base64.getEncoder().encodeToString(encodeProperties(encode(next))))
            setProperty("moves",moves.size.toString())
            moves.forEachIndexed { i,(from,to)->
                setProperty("$i.from",from.relativeTo(runtime).invariantSeparatorsPath)
                setProperty("$i.to",to.relativeTo(runtime).invariantSeparatorsPath)
            }
        }
        atomic(journal,encodeProperties(p))
        moves.forEachIndexed { i,(from,to)-> to.parentFile!!.mkdirs(); Files.move(from.toPath(),to.toPath(),ATOMIC_MOVE); moved(i) }
        atomic(manifest,encodeProperties(encode(next))); check(journal.delete())
    }
    private fun encode(entries: List<Entry>)=Properties().apply {
        setProperty("format","1"); setProperty("side",side); setProperty("count",entries.size.toString())
        entries.forEachIndexed { i,e ->
            setProperty("$i.name",e.name); setProperty("$i.side",e.side); setProperty("$i.version",e.version); setProperty("$i.enabled",e.enabled.toString())
            setProperty("$i.files",e.files.size.toString())
            e.files.entries.sortedBy { it.key }.forEachIndexed { j,(path,sha)->setProperty("$i.file.$j",path); setProperty("$i.sha.$j",sha) }
            e.properties.forEach { (k,v)->setProperty("$i.property.$k",v) }
        }
    }
    private fun decode(p: Properties): List<Entry> {
        if(p.isEmpty) return emptyList()
        require(p.getProperty("format")=="1" && p.getProperty("side")==side) { "Unsupported mod manifest or wrong side" }
        val count=p.getProperty("count").toInt().also { require(it in 0..100) }
        return List(count) { i ->
            val name=p.getProperty("$i.name").also(::validName)
            require(p.getProperty("$i.side")==side)
            val files=(0 until p.getProperty("$i.files").toInt().also { require(it in 1..10000) }).associate { j ->
                val path=p.getProperty("$i.file.$j").also(::validPath)
                require(path in units(name) || path.startsWith("$name/"))
                path to p.getProperty("$i.sha.$j").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            }
            Entry(name,side,p.getProperty("$i.version"),p.getProperty("$i.enabled").toBooleanStrict(),files,
                p.stringPropertyNames().filter { it.startsWith("$i.property.") }.associate { it.removePrefix("$i.property.") to p.getProperty(it) })
        }.also { require(it.map { e->e.name.lowercase(Locale.ROOT) }.distinct().size==it.size) }
    }
    private fun ownedPath(path: String): File {
        validPath(path); require(path.startsWith("mods/") || path.startsWith("android-mods/disabled/") || path.startsWith("android-mods/import-"))
        return File(runtime,path).also { require(it.canonicalFile.toPath().startsWith(runtime.canonicalFile.toPath())) }
    }
    private fun inventory(root: File, paths: List<File>): Map<String,String> = paths.flatMap { file ->
        if(file.isDirectory) file.walkTopDown().filter { it.isFile }.toList() else listOf(file)
    }.associate { file ->
        require(file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        var ancestor=file
        while(ancestor!=root) { require(!Files.isSymbolicLink(ancestor.toPath())); ancestor=ancestor.parentFile }
        val relative=file.relativeTo(root).invariantSeparatorsPath.also(::validPath)
        relative to sha(file)
    }
    private fun unzip(input: InputStream, root: File) {
        var count=0; var bytes=0L; val seen=hashSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            val buffer=ByteArray(65536)
            while(true) {
                val entry=zip.nextEntry ?: break
                require(++count<=10000) { "Mod ZIP has too many entries" }
                val name=entry.name.removeSuffix("/").also(::validPath)
                require(seen.add(name.lowercase(Locale.ROOT))) { "Duplicate ZIP path: $name" }
                val file=File(root,name)
                if(entry.isDirectory) check(file.isDirectory||file.mkdirs()) else {
                    file.parentFile!!.mkdirs()
                    file.outputStream().use { out -> while(true) {
                        if(Thread.currentThread().isInterrupted) throw InterruptedException("Mod import cancelled")
                        val n=zip.read(buffer); if(n<0) break
                        bytes+=n; require(bytes<=512L*1024*1024) { "Mod ZIP exceeds 512 MiB" }
                        check(root.usableSpace>64L*1024*1024) { "Free more internal storage" }; out.write(buffer,0,n)
                    } }
                }; zip.closeEntry()
            }
        }
    }
    companion object {
        const val SERVER_LOADER_SHA="44f7c9adc2dfbe2de45d7bc22a6ef8448549cde3f1f164b5c59a094948e6bacf"
        private fun units(name: String)=listOf("$name.properties","$name.config",name)
        private fun documentation(name: String)=name.startsWith("README",true)||name.startsWith("LICENSE",true)||name.startsWith("CHANGELOG",true)||name.startsWith("NOTICE",true)
        private fun validName(name: String) { require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"))) { "Unsupported mod name: $name" } }
        private fun validPath(path: String) { require(path.isNotBlank() && path.length<=1024 && !path.startsWith('/') && ':' !in path && '\\' !in path && path.none { it.isISOControl() } && path.split('/').none { it in listOf("",".","..") }) { "Invalid mod path" } }
        private fun sha(file: File): String {
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input-> val b=ByteArray(65536); while(true) { val n=input.read(b); if(n<0) break; digest.update(b,0,n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        private fun readProperties(file: File)=Properties().apply { if(file.isFile) file.inputStream().use { load(it) } }
        private fun encodeProperties(p: Properties)=ByteArrayOutputStream().also { p.store(it,"Wurm Android mod manifest") }.toByteArray()
        private fun atomic(file: File, bytes: ByteArray) {
            file.parentFile!!.mkdirs(); val tmp=File(file.parentFile,file.name+".pending")
            FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
            Files.move(tmp.toPath(),file.toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
        }
    }
}
