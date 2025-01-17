addSbtPlugin("com.eed3si9n" % "sbt-buildinfo"   % "0.10.0")  // provides version information to copy into main class
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")   // binary compatibility testing
addSbtPlugin("ch.epfl.lamp" % "sbt-dotty"       % "0.5.4")   // cross-compile for dotty
addSbtPlugin("org.scala-js" % "sbt-scalajs"     % "1.5.1")   // cross-compile for scala.js
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")

