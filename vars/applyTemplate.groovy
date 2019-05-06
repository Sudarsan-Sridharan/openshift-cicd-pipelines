#!/usr/bin/env groovy

def call(parameters) {
    openshift.withCluster(parameters.clusterUrl, parameters.credentialsId) {
        openshift.withProject(parameters.project) {
            def objects = openshift.process(steps.readFile(file: parameters.template), "-p APP_NAME=${parameters.application}", "--param-file=${parameters.parameters}", "--ignore-unknown-parameters")

            openshift.apply(process(filter(objects, parameters.createBuildObjects)))
            
            if (parameters.replaceConfig)
                openshift.replace(openshift.process(readFile(file: parameters.replaceConfig), "-p APP_NAME=${parameters.application}"))
            
            if (parameters.deploymentPatch)
                openshift.patch("dc/${parameters.application}", "'${readFile(file: parameters.deploymentPatch)}'")                            
        }
    }
}

def process(objects) {
    for (o in objects) {
        if (o.kind.equals("DeploymentConfig")) {
            def dc = openshift.selector("dc", o.metadata.name)

            if (dc.exists()) {
                o.spec.template.spec.containers[0].image = dc.object().spec.template.spec.containers[0].image
            }

            o.spec.triggers = []
        } 
    }

    return objects
}

def filter(objects, createBuildObjects) {
    if (createBuildObjects == true) {
        return objects
    }
    else {
        def filteredObjects = []

        for (o in objects) {
            if (o.kind != "BuildConfig" && o.kind != "ImageStream")  {
                filteredObjects.add(o)
            } 
        }

        return filteredObjects
    }
}