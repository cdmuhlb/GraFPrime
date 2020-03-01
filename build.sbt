name := "grafprime"

organization := "cornell.edu"

maintainer := "cdm89@cornell.edu"

scalaVersion := "2.13.1"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.2.0"

libraryDependencies += "org.eclipse.elk" % "org.eclipse.elk.core" % "0.6.0"

libraryDependencies += "org.eclipse.elk" % "org.eclipse.elk.alg.layered" % "0.6.0"

// SBT doesn't know how to interpret ELK's transitive dependency on Guava, which
// specifies the version [15.0,19.0); therefore, we force the version to be the
// latest in that range.
dependencyOverrides += "com.google.guava" % "guava" % "18.0"

enablePlugins(JavaAppPackaging)
