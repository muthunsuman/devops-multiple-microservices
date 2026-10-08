def call(String service, String registry, String region, String tag) {
    if (!(service ==~ /[a-z0-9-]+/)) error("Invalid service name: ${service}")
    sh """
      set -eu
      aws ecr get-login-password --region '${region}' |
        docker login --username AWS --password-stdin '${registry}'
      docker build --progress=plain -f docker/Dockerfile -t '${registry}/${service}:${tag}' 'services/${service}'
      docker push '${registry}/${service}:${tag}'
    """
}
