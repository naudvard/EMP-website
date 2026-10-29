package fr.nathanaudvard.emp.web.assets

// The name holds a hash of the content: a new version has a new name
class Asset(val name: String, val content: String)

// In loading order: the components and the base styles are set on the tokens
val stylesheets = listOf(tokensCss, bundleCss, baseCss)

// Loaded deferred by every page: the pages work without it
val script = empJs
