val baseName = "ScalaCollider"

val PROJECT_VERSION           = "1.22.2"
val scalaColliderSwingVersion = "1.32.0"

val lOSC                = RootProject(uri( "git://github.com/Sciss/ScalaOSC.git#v1.1.4"))
val lAudioFile          = RootProject(uri( "git://github.com/Sciss/ScalaAudioFile.git#v1.4.5"))
val lUGens              = RootProject(uri( "git://github.com/Sciss/ScalaColliderUGens.git#v1.16.3"))
val lScalaCollider      = RootProject(uri(s"git://github.com/Sciss/$baseName.git#v${PROJECT_VERSION}"))
val lScalaColliderSwing = RootProject(uri(s"git://github.com/Sciss/ScalaColliderSwing.git#v$scalaColliderSwingVersion"))

scalaVersion in ThisBuild := "2.11.8"

paradoxProperties in ThisBuild += "swingversion" -> "FOO-BAR"

val root = (project in file("."))
  .settings(unidocSettings)
  .enablePlugins(ParadoxSitePlugin, SiteScaladocPlugin)
  .settings(ghpages.settings)
  .settings(
    name                 := baseName,
    version              := PROJECT_VERSION,
    siteSubdirName in SiteScaladoc    := "latest/api",
    mappings in packageDoc in Compile := (mappings  in (ScalaUnidoc, packageDoc)).value,
    git.remoteRepo       := s"git@github.com:Sciss/${name.value}.git",
    git.gitCurrentBranch := "master",
    paradoxTheme         := Some(builtinParadoxTheme("generic")),
    paradoxProperties in Compile ++= Map(
      "snippet.base_dir" -> s"${baseDirectory.value}/snippets/src/main",
      "swingversion" -> scalaColliderSwingVersion
    ),
    scalacOptions in (Compile, doc) ++= Seq(
      "-skip-packages", Seq(
        "de.sciss.osc.impl", 
        "de.sciss.synth.impl",
        "snippets"
      ).mkString(":"),
      "-doc-title", s"${baseName} ${PROJECT_VERSION} API"
    )
  )
  .aggregate(lOSC, lAudioFile, lUGens, lScalaCollider /* , lScalaColliderSwing */)

val snippets = (project in file("snippets"))
  .dependsOn(lScalaCollider)
  .settings(
    name := s"$baseName-Snippets"
  )
