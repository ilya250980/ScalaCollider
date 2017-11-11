val baseName = "ScalaCollider"

val PROJECT_VERSION           = "1.23.0"
val scalaColliderSwingVersion = "1.35.0"
val ugensVersion              = "1.17.1"
val audioFileVersion          = "1.4.6"
val oscVersion                = "1.1.5"

val lOSC                = RootProject(uri( "git://github.com/Sciss/ScalaOSC.git#v$oscVersion"))
val lAudioFile          = RootProject(uri( "git://github.com/Sciss/ScalaAudioFile.git#v$audioFileVersion"))
val lUGens              = RootProject(uri( "git://github.com/Sciss/ScalaColliderUGens.git#v$ugensVersion"))
val lScalaCollider      = RootProject(uri(s"git://github.com/Sciss/$baseName.git#v${PROJECT_VERSION}"))
val lScalaColliderSwing = RootProject(uri(s"git://github.com/Sciss/ScalaColliderSwing.git#v$scalaColliderSwingVersion"))

scalaVersion in ThisBuild := "2.12.4"

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
    paradoxProperties in Paradox ++= Map(
      "snippet.base_dir"        -> s"${baseDirectory.value}/snippets/src/main",
      "swingversion"            -> scalaColliderSwingVersion,
      "extref.swingdl.base_url" -> s"https://github.com/Sciss/ScalaColliderSwing/releases/download/v${scalaColliderSwingVersion}/ScalaColliderSwing_${scalaColliderSwingVersion}%s"
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
  .aggregate(lOSC, lAudioFile, lUGens, lScalaCollider, lScalaColliderSwing)

val snippets = (project in file("snippets"))
  .dependsOn(lScalaCollider)
  .settings(
    name := s"$baseName-Snippets"
  )
