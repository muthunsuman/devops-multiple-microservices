def call(String service) {
    if (!(service ==~ /[a-z0-9-]+/)) error("Invalid service name: ${service}")
    dir("services/${service}") { sh 'mvn -B clean verify' }
}
