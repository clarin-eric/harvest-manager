# 1.2.0
Changes compared to 1.1.0

- for XSLT [SaxonUtils](https://github.com/TLA-FLAT/SaxonUtils) is used, so extensionfunctions for dynamic XPath evaluation can easily be used
- the Transform action now provides access to the configuration to the stylesheet, so records can be filtered, transformed on the basis of info from the configuration
- the JSON API of the Centre Registry is used instead of the XML one
- no external processing is needed anymore to complete the mapping from directory names to endpoint and centre info
- now uses Java 11
- skip invalid characters encountered in the XML
- retry-delay is now in seconds instead of milliseconds
- extended documentation

# 1.1.0
Changes compared to 0.6.2

- Added a `dry-run` configuration option to run without actually harvesting from the endpoints
- Processing of OAI-PMH set information from the Centre Registry (#26)
- Added unit tests for configuration; extended tests for the registry reader
- Added the `build.sh` script to build the harvest manager using Maven in a docker container
