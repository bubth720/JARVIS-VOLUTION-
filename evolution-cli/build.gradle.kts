plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":evolution-core")) }
application { mainClass = "fr.jarvis.evolution.cli.MainKt" }
