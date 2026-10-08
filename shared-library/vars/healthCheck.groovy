def call(String url) {
    if (!(url ==~ /^https?:\/\/.+/)) error("Health-check URL must use http or https")
    sh "./scripts/health-check.sh '${url}'"
}
