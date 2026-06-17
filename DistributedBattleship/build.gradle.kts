plugins {
    id("java")
}

group = "distributed.battleship"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
    manifest {
        // Modifica inserendo il package e la classe principale esatta del tuo progetto
        attributes["Main-Class"] = "distributed.battleship.Main"
    }

    archiveBaseName.set("DistributedBattleship")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Questa è la sintassi specifica Kotlin DSL per scompattare e unire le dipendenze
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    with(tasks.jar.get())
}