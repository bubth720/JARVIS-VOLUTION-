plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":evolution-core")); implementation(project(":evolution-sandbox")) }
application { mainClass = "fr.jarvis.evolution.cli.MainKt" }
