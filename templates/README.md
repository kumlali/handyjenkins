Put project independent templates here.

A template is basically a Jenkinsfile that can be loaded by other Jenkinsfiles:

    load("/hj/templates/company.template")

As a general rule, template files should use "template" as file extentions (e.g. company.template)