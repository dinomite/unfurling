A service to provide [Slack](https://slack.com/)-like [unfurling](https://medium.com/slack-developer-blog/everything-you-ever-wanted-to-know-about-unfurling-but-were-afraid-to-ask-or-how-to-make-your-e64b4bb9254).

Add it to your `build.gradle`:

    compile 'net.dinomite.web:unfurling:1.0.2'

Use it by creating an `UnfurlingService` and asking it to `unfurl()` a `URI`:

    val unfurlingService = UnfurlingService(HttpClients.createDefault())
    val unfurled = unfurlingService.unfurl(URI("https://twitter.com/dinomite"))
    println(unfurled.url) // https://twitter.com/dinomite
    println(unfurled.title) // Rev. Drew Stephens
    println(unfurled.image) // https://pbs.twimg.com/profile_images/1144814297/Drew_955x955.jpg
    println(unfurled.description) // Grand High Figurehead, Church of Empirical Evidence
