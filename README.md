# GraFPrime
Visualize topologies of F Prime applications

JPL's [F Prime framework](https://github.com/nasa/fprime) uses XML files to define component interfaces (input and output ports) and to declare the components and connections that make up an application's Topology.  However, the framework does not provide a way to visualize these connections (JPL employees likely rely on the commercial product Magic Draw).  This makes it difficult to understand how existing applications are put together.  The **GraFPrime** tool parses these XML files and produces SVG graphs visualizing an application's Topology.

## Getting started
**GraFPrime** is written in Scala and can be built using [sbt](https://www.scala-sbt.org/).  Compilation requires a Java SE 8+ JDK and an sbt launcher.  To run the resulting executable, a Java SE 8+ JRE is all that is required.

To build an executable, run:

    sbt stage

This will create `target/universal/stage/bin/grafprime`, which can be invoked on the command line locally.  To create a redistributable package, run `sbt universal:packageBin`; the package will be saved to `target/universal/grafprime-0.1.0-SNAPSHOT.zip`.

    Usage: grafprime <app_xml> <output_file> [fprime_dir]

By default, `fprime_dir` will be your current working directory.  An example invocation might therefore look like:

    cd $FPRIME_DIR
    grafprime Ref/Top/RefTopologyAppAi.xml RefTopology.svg

The resulting SVG file can be viewed in any modern web browser.  In most browsers, clicking on an edge in the graph will highlight that edge, allowing you to trace the connection from source to target.

## Limitations
At the moment, some ports are not found for some components, resulting in warnings printed to the console and missing ports/edges in the resulting graph.
