package com.jvoice.core.i18n

/**
 * Bilingual app chrome - every fixed label, button, tab, empty state and toast.
 *
 * Deliberately typed vals rather than a `Map<String, LocalizedText>`: a typo in a
 * string key is a runtime blank, a typo in a property name is a compile error.
 * Grouped by the surface that shows them so a screen pulls from one place.
 *
 * Content (headlines, bodies, questions) does NOT live here - that travels with
 * the data as [LocalizedText] fields on the models.
 */
object Strings {

    /* ------------------------------------------------------------- language */

    object Language {
        val title = lt("Language", "భాష")
        val chooseTitle = lt("Choose your language", "మీ భాషను ఎంచుకోండి")
        val chooseSubtitle = lt(
            "News and study material are available in both languages. You can change this any time from your profile.",
            "వార్తలు, స్టడీ మెటీరియల్ రెండు భాషల్లోనూ అందుబాటులో ఉన్నాయి. ప్రొఫైల్ నుంచి ఎప్పుడైనా మార్చుకోవచ్చు."
        )
        val readingIn = lt("Reading in", "చదువుతున్న భాష")
        val switchTo = lt("Switch to", "మార్చు")
        val changedTo = lt("Language changed to", "భాష మార్చబడింది")
        val appliesEverywhere = lt(
            "Applies to news, study material and exams",
            "వార్తలు, స్టడీ మెటీరియల్, పరీక్షలకు వర్తిస్తుంది"
        )
        val notTranslated = lt("Not available in this language yet", "ఈ భాషలో ఇంకా అందుబాటులో లేదు")
        val showingOther = lt("Showing the other language", "మరో భాషలో చూపుతోంది")
        val needsTranslation = lt("Needs translation", "అనువాదం కావాలి")
        val translationMissing = lt("Translation missing", "అనువాదం లేదు")
        val bothLanguages = lt("Both languages", "రెండు భాషలు")
    }

    /* ------------------------------------------------------------ staff sign-in */

    object Auth {
        val staffSignIn = lt("Staff sign in", "సిబ్బంది సైన్-ఇన్")
        val signIn = lt("Sign in", "సైన్ ఇన్")
        val signOut = lt("Sign out", "సైన్ అవుట్")
        val subtitle = lt(
            "For reporters, editors and the desk. Readers do not need an account.",
            "రిపోర్టర్లు, ఎడిటర్లు, డెస్క్ కోసం. పాఠకులకు ఖాతా అవసరం లేదు."
        )
        val loginId = lt("Login ID", "లాగిన్ ఐడీ")
        val loginIdHint = lt("e.g. jv01r001", "ఉదా. jv01r001")
        val password = lt("Password", "పాస్‌వర్డ్")
        val showPassword = lt("Show password", "పాస్‌వర్డ్ చూపు")
        val hidePassword = lt("Hide password", "పాస్‌వర్డ్ దాచు")
        val signingIn = lt("Signing in…", "సైన్ ఇన్ అవుతోంది…")
        val forgotPassword = lt("Forgot password?", "పాస్‌వర్డ్ మరచిపోయారా?")
        val resetTitle = lt("Request a password reset", "పాస్‌వర్డ్ రీసెట్ అభ్యర్థించండి")
        val resetBody = lt(
            "There is no self-service reset. Leave your mobile number and the desk will call you back.",
            "స్వయంగా రీసెట్ చేసుకోవడం కుదరదు. మీ మొబైల్ నంబర్ ఇవ్వండి, డెస్క్ మీకు కాల్ చేస్తుంది."
        )
        val sendRequest = lt("Send request", "అభ్యర్థన పంపు")
        val continueAsReader = lt("Continue as reader", "పాఠకుడిగా కొనసాగు")
        val noAccountNeeded = lt("No account needed", "ఖాతా అవసరం లేదు")
        val sessionEnded = lt(
            "Your session was ended by the desk.",
            "మీ సెషన్‌ను డెస్క్ ముగించింది."
        )
        val accountDisabled = lt(
            "This account has been disabled. Contact the administrator.",
            "ఈ ఖాతా నిలిపివేయబడింది. అడ్మినిస్ట్రేటర్‌ను సంప్రదించండి."
        )
        val updateTitle = lt("Update required", "అప్‌డేట్ కావాలి")
        val updateBody = lt(
            "A newer version of J Voice is required to continue.",
            "కొనసాగడానికి J Voice కొత్త వెర్షన్ కావాలి."
        )
        val later = lt("Later", "తర్వాత")
        val demoModeNotice = lt(
            "Firebase is not configured - showing the local demo logins.",
            "Firebase కాన్ఫిగర్ చేయలేదు - లోకల్ డెమో లాగిన్లు చూపుతోంది."
        )
    }

    /* ---------------------------------------------------------- first launch */

    object FirstRun {
        val welcome = lt("Welcome to J Voice", "J Voice కి స్వాగతం")
        val welcomeSubtitle = lt(
            "Telugu news and exam preparation in one app.",
            "తెలుగు వార్తలు, పరీక్షల ప్రిపరేషన్ ఒకే యాప్‌లో."
        )
        val notificationTitle = lt("Stay updated", "అప్‌డేట్‌గా ఉండండి")
        val notificationBody = lt(
            "Allow notifications for breaking news, exam alerts and daily test reminders.",
            "బ్రేకింగ్ న్యూస్, పరీక్షల అలర్ట్‌లు, రోజువారీ టెస్ట్ రిమైండర్ల కోసం నోటిఫికేషన్‌లను అనుమతించండి."
        )
        val allowNotifications = lt("Allow notifications", "నోటిఫికేషన్‌లను అనుమతించు")
        val notNow = lt("Not now", "ఇప్పుడు కాదు")
        val continueLabel = lt("Continue", "కొనసాగించు")
        val getStarted = lt("Get started", "ప్రారంభించండి")
        val aboutYouTitle = lt("Tell us about you", "మీ గురించి చెప్పండి")
        val aboutYouSubtitle = lt(
            "Your name appears on your comments. Your location picks the news you see first.",
            "మీ పేరు మీ కామెంట్లపై కనిపిస్తుంది. మీ ప్రాంతం ప్రకారం వార్తలు ముందుగా చూపిస్తాం."
        )
        val yourName = lt("Your name", "మీ పేరు")
        val namePlaceholder = lt("Optional", "ఐచ్ఛికం")
        val yourLocation = lt("Your location", "మీ ప్రాంతం")
        val notificationsOn = lt("Notifications are on", "నోటిఫికేషన్‌లు ఆన్‌లో ఉన్నాయి")
        val notificationsOff = lt("Notifications are off", "నోటిఫికేషన్‌లు ఆఫ్‌లో ఉన్నాయి")
        val notificationsHint = lt(
            "You can turn these on later from your profile.",
            "వీటిని తర్వాత ప్రొఫైల్ నుంచి ఆన్ చేసుకోవచ్చు."
        )
    }

    /* --------------------------------------------------------------- common */

    object Common {
        val back = lt("Back", "వెనుకకు")
        val goBack = lt("Go back", "వెనుకకు వెళ్లు")
        val cancel = lt("Cancel", "రద్దు")
        val confirm = lt("Confirm", "ఖరారు చేయి")
        val save = lt("Save", "సేవ్ చేయి")
        val delete = lt("Delete", "తొలగించు")
        val remove = lt("Remove", "తీసివేయి")
        val edit = lt("Edit", "మార్చు")
        val done = lt("Done", "పూర్తయింది")
        val next = lt("Next", "తరువాత")
        val previous = lt("Previous", "మునుపటి")
        val submit = lt("Submit", "సమర్పించు")
        val retry = lt("Retry", "మళ్లీ ప్రయత్నించు")
        val clear = lt("Clear", "క్లియర్")
        val clearAll = lt("Clear all", "అన్నీ క్లియర్ చేయి")
        val clearFilters = lt("Clear filters", "ఫిల్టర్‌లు క్లియర్ చేయి")
        val seeAll = lt("See all", "అన్నీ చూడు")
        val view = lt("View", "చూడు")
        val open = lt("Open", "తెరువు")
        val share = lt("Share", "షేర్ చేయి")
        val search = lt("Search", "వెతుకు")
        val filter = lt("Filter", "ఫిల్టర్")
        val all = lt("All", "అన్నీ")
        val none = lt("None", "ఏమీ లేదు")
        val today = lt("Today", "ఈ రోజు")
        val yesterday = lt("Yesterday", "నిన్న")
        val justNow = lt("Just now", "ఇప్పుడే")
        val active = lt("Active", "యాక్టివ్")
        val inactive = lt("Inactive", "ఇన్‌యాక్టివ్")
        val loading = lt("Loading…", "లోడ్ అవుతోంది…")
        val refreshing = lt("Refreshing…", "రిఫ్రెష్ అవుతోంది…")
        val nothingHereYet = lt("Nothing here yet", "ఇక్కడ ఇంకా ఏమీ లేదు")
        val noResults = lt("No results", "ఫలితాలు లేవు")
        val noMatches = lt("No matches", "సరిపోలికలు లేవు")
        val somethingWentWrong = lt("Something went wrong", "ఏదో పొరపాటు జరిగింది")
        val openMenu = lt("Open menu", "మెనూ తెరువు")
        val switchRole = lt("Switch role", "రోల్ మార్చు")
        val switchRoleQuestion = lt("Switch role?", "రోల్ మార్చాలా?")
        val switchLabel = lt("Switch", "మార్చు")
        val preferences = lt("Preferences", "ప్రాధాన్యతలు")
        val darkMode = lt("Dark mode", "డార్క్ మోడ్")
        val darkModeHint = lt(
            "Follows the system unless changed here",
            "ఇక్కడ మార్చకపోతే సిస్టమ్‌ను అనుసరిస్తుంది"
        )
        val notifications = lt("Notifications", "నోటిఫికేషన్‌లు")
        val noNotifications = lt("No notifications", "నోటిఫికేషన్‌లు లేవు")
        val markAllRead = lt("Mark all read", "అన్నీ చదివినట్టు గుర్తించు")
        val profile = lt("Profile", "ప్రొఫైల్")
        val about = lt("About", "గురించి")
        val demoData = lt("DEMO DATA", "డెమో డేటా")
        val localDemoToggle = lt("Local demo toggle only", "లోకల్ డెమో టోగుల్ మాత్రమే")
        val category = lt("Category", "విభాగం")
        val categories = lt("Categories", "విభాగాలు")
        val location = lt("Location", "ప్రాంతం")
        val member = lt("Member", "సభ్యుడు")
        val you = lt("You", "మీరు")
        val exit = lt("Exit", "నిష్క్రమించు")
        val change = lt("Change", "మార్చు")
        val english = lt("English", "ఇంగ్లీష్")
        val telugu = lt("Telugu", "తెలుగు")
    }

    /* ----------------------------------------------------------------- news */

    object News {
        val moduleName = lt("News", "వార్తలు")
        val tabNews = lt("News", "వార్తలు")
        val tabClips = lt("Clips", "క్లిప్‌లు")
        val tabStudy = lt("Study", "చదువు")
        val tabProfile = lt("Profile", "ప్రొఫైల్")
        val latestNews = lt("Latest News", "తాజా వార్తలు")
        val trendingNow = lt("Trending Now", "ట్రెండింగ్")
        val breakingNews = lt("Breaking News", "బ్రేకింగ్ న్యూస్")
        val breakingBadge = lt("BREAKING", "బ్రేకింగ్")
        val pinnedBadge = lt("PINNED", "పిన్ చేసినవి")
        val newBadge = lt("NEW", "కొత్తది")
        val relatedNews = lt("Related News", "సంబంధిత వార్తలు")
        val sameCategory = lt("Same category", "అదే విభాగం")
        val readFullStory = lt("Read the full story", "పూర్తి కథనం చదవండి")
        val myNews = lt("My news", "నా వార్తలు")
        val savedNews = lt("Saved news", "సేవ్ చేసిన వార్తలు")
        val saved = lt("Saved", "సేవ్ చేసినవి")
        val saveArticle = lt("Save article", "కథనాన్ని సేవ్ చేయి")
        val removeBookmark = lt("Remove bookmark", "బుక్‌మార్క్ తీసివేయి")
        val savedToBookmarks = lt("Saved to your bookmarks", "మీ బుక్‌మార్క్‌లకు సేవ్ అయింది")
        val removedFromBookmarks = lt("Removed from bookmarks", "బుక్‌మార్క్‌ల నుంచి తీసివేయబడింది")
        val noSavedArticles = lt("No saved articles", "సేవ్ చేసిన కథనాలు లేవు")
        val clearAllBookmarks = lt("Clear all bookmarks?", "అన్ని బుక్‌మార్క్‌లను క్లియర్ చేయాలా?")
        val allBookmarksCleared = lt("All bookmarks cleared", "అన్ని బుక్‌మార్క్‌లు క్లియర్ అయ్యాయి")
        val loadingNews = lt("Loading news…", "వార్తలు లోడ్ అవుతున్నాయి…")
        val noPublishedNews = lt("No published news yet", "ఇంకా ప్రచురించిన వార్తలు లేవు")
        val nothingPublishedHere = lt("Nothing published here yet", "ఇక్కడ ఇంకా ఏమీ ప్రచురించలేదు")
        val articleNotAvailable = lt("Article not available", "కథనం అందుబాటులో లేదు")
        val browseByCategory = lt("Browse news by category", "విభాగం ప్రకారం వార్తలు చూడండి")
        val noCategoriesEnabled = lt("No categories enabled", "ఏ విభాగం ప్రారంభించలేదు")
        val filterByCategory = lt("Filter by category", "విభాగం ప్రకారం ఫిల్టర్")
        val filterByLocation = lt("Filter by location", "ప్రాంతం ప్రకారం ఫిల్టర్")
        val searchNews = lt("Search news", "వార్తల్లో వెతకండి")
        val searchPlaceholder = lt("Title, category or location…", "శీర్షిక, విభాగం లేదా ప్రాంతం…")
        val searchTheArchive = lt("Search the demo archive", "డెమో ఆర్కైవ్‌లో వెతకండి")
        val pullToRefresh = lt("Pull to refresh", "రిఫ్రెష్ కోసం లాగండి")
        val endOfFeed = lt("You have reached the end of the demo feed.", "డెమో ఫీడ్ చివరకు వచ్చారు.")
        val caughtUp = lt("You are all caught up — starting again", "అన్నీ చదివేశారు — మళ్లీ మొదలు")
        val classicFeed = lt("Classic feed", "క్లాసిక్ ఫీడ్")
        val moreIn = lt("More in", "ఇంకా")
        val updated = lt("Updated", "అప్‌డేట్ అయింది")
        val views = lt("views", "వ్యూస్")
        val guestReader = lt("Guest reader", "అతిథి పాఠకుడు")
        val preferredLocation = lt("Preferred location", "ఇష్టమైన ప్రాంతం")
        val changeLocation = lt("Change location", "ప్రాంతం మార్చు")
        val locationSetTo = lt("Location set to", "ప్రాంతం సెట్ అయింది")
        val anywhere = lt("Anywhere", "ఎక్కడైనా")
        val breakingAlerts = lt("Breaking news alerts", "బ్రేకింగ్ న్యూస్ అలర్ట్‌లు")
        val breakingAlertsEmpty = lt(
            "Breaking news alerts will appear here.",
            "బ్రేకింగ్ న్యూస్ అలర్ట్‌లు ఇక్కడ కనిపిస్తాయి."
        )
        val pushAlerts = lt("Push alerts", "పుష్ అలర్ట్‌లు")
        val sound = lt("Sound", "శబ్దం")
        val muted = lt("Muted", "మ్యూట్ చేసినది")

        // clips
        val noClipsHere = lt("No clips here", "ఇక్కడ క్లిప్‌లు లేవు")
        val noClipsInCategory = lt(
            "No video clips in this category yet.",
            "ఈ విభాగంలో ఇంకా వీడియో క్లిప్‌లు లేవు."
        )
        val clipSaved = lt("Clip saved", "క్లిప్ సేవ్ అయింది")
        val removedFromSavedClips = lt(
            "Removed from saved clips",
            "సేవ్ చేసిన క్లిప్‌ల నుంచి తీసివేయబడింది"
        )
        val sharingDisabled = lt(
            "Sharing is disabled for demo clips",
            "డెమో క్లిప్‌లకు షేరింగ్ నిలిపివేయబడింది"
        )
        val play = lt("Play", "ప్లే")
        val video = lt("Video", "వీడియో")

        // comments
        val comments = lt("Comments", "కామెంట్‌లు")
        val comment = lt("Comment", "కామెంట్")
        val addComment = lt("Add a comment…", "కామెంట్ రాయండి…")
        val postComment = lt("Post comment", "కామెంట్ పోస్ట్ చేయి")
        val commentPosted = lt("Comment posted", "కామెంట్ పోస్ట్ అయింది")
        val commentDeleted = lt("Comment deleted", "కామెంట్ తొలగించబడింది")
        val deleteComment = lt("Delete comment", "కామెంట్ తొలగించు")
        val likeComment = lt("Like comment", "కామెంట్‌కు లైక్")
        val noCommentsYet = lt("No comments yet", "ఇంకా కామెంట్‌లు లేవు")

        // reporting
        val reportArticle = lt("Report article", "కథనాన్ని రిపోర్ట్ చేయి")
        val reportThisStory = lt("Report this story", "ఈ కథనాన్ని రిపోర్ట్ చేయి")
        val report = lt("Report", "రిపోర్ట్")
        val submitReport = lt("Submit report", "రిపోర్ట్ సమర్పించు")
        val suggestCorrection = lt("Suggest a correction (optional)", "సరిదిద్దుబాటు సూచించండి (ఐచ్ఛికం)")
        val reportThanks = lt(
            "Thanks — your report was sent to the desk",
            "ధన్యవాదాలు — మీ రిపోర్ట్ డెస్క్‌కు పంపబడింది"
        )
        val reportedToModerators = lt(
            "Reported to moderators (demo)",
            "మోడరేటర్లకు రిపోర్ట్ చేయబడింది (డెమో)"
        )

        val editorNote = lt("Editor note: ", "ఎడిటర్ నోట్: ")
        val rejected = lt("Rejected: ", "తిరస్కరించబడింది: ")
    }

    /* ---------------------------------------------------------------- study */

    object Study {
        val moduleName = lt("Study", "చదువు")
        val appName = lt("J Voice Study", "J Voice స్టడీ")
        val tabHome = lt("Home", "హోమ్")
        val tabStudy = lt("Study", "చదువు")
        val tabExams = lt("Exams", "పరీక్షలు")
        val tabRanks = lt("Ranks", "ర్యాంకులు")
        val tabProfile = lt("Profile", "ప్రొఫైల్")
        val tabNews = lt("News", "వార్తలు")

        val goodMorning = lt("Good Morning", "శుభోదయం")
        val goodAfternoon = lt("Good Afternoon", "శుభ మధ్యాహ్నం")
        val goodEvening = lt("Good Evening", "శుభ సాయంత్రం")

        val preparingDashboard = lt("Preparing your dashboard…", "మీ డాష్‌బోర్డ్ సిద్ధమవుతోంది…")
        val backToNews = lt("Back to News", "వార్తలకు తిరిగి")
        val switchBackToNews = lt(
            "Switch back to the news experience",
            "వార్తల అనుభవానికి తిరిగి వెళ్లు"
        )

        // exam track selection
        val chooseExam = lt("Choose exam", "పరీక్షను ఎంచుకోండి")
        val chooseExamFirst = lt("Choose your exam first", "ముందు మీ పరీక్షను ఎంచుకోండి")
        val chooseExamPrompt = lt(
            "Choose the exam you are preparing for.",
            "మీరు ప్రిపేర్ అవుతున్న పరీక్షను ఎంచుకోండి."
        )
        val changeExam = lt("Change exam", "పరీక్షను మార్చు")
        val switchExam = lt("Switch exam", "పరీక్షను మార్చు")
        val currentExam = lt("Current exam", "ప్రస్తుత పరీక్ష")
        val targetExam = lt("Target exam", "లక్ష్య పరీక్ష")
        val noExamSelected = lt("No exam selected", "పరీక్ష ఎంచుకోలేదు")
        val notChosenYet = lt("Not chosen yet", "ఇంకా ఎంచుకోలేదు")
        val noExamsConfigured = lt("No exams configured", "పరీక్షలు కాన్ఫిగర్ చేయలేదు")
        val noExamsHere = lt("No exams here", "ఇక్కడ పరీక్షలు లేవు")
        val nothingToPrepareFor = lt("Nothing to prepare for yet.", "ఇంకా ప్రిపేర్ అవడానికి ఏమీ లేదు.")
        val searchExams = lt("Search Constable, Group-4, SSC…", "కానిస్టేబుల్, గ్రూప్-4, SSC… వెతకండి")
        val everythingFor = lt("Everything for", "కోసం అన్నీ")
        val allExams = lt("All exams", "అన్ని పరీక్షలు")
        val thisExam = lt("This exam", "ఈ పరీక్ష")
        val onThisExamOnly = lt("On this exam only", "ఈ పరీక్షకు మాత్రమే")

        // syllabus & content
        val syllabus = lt("Syllabus", "సిలబస్")
        val subjects = lt("Subjects", "సబ్జెక్టులు")
        val subject = lt("Subject", "సబ్జెక్ట్")
        val topics = lt("Topics", "టాపిక్‌లు")
        val topic = lt("Topic", "టాపిక్")
        val openTopic = lt("Open topic", "టాపిక్ తెరువు")
        val articles = lt("Articles", "కథనాలు")
        val content = lt("Content", "కంటెంట్")
        val startLearning = lt("Start Learning", "నేర్చుకోవడం ప్రారంభించు")
        val continueStudying = lt("Continue Studying", "చదువు కొనసాగించు")
        val importantPoints = lt("Important Points", "ముఖ్యాంశాలు")
        val keyFormulas = lt("Key Formulas", "ముఖ్య సూత్రాలు")
        val examples = lt("Examples", "ఉదాహరణలు")
        val explanation = lt("Explanation", "వివరణ")
        val author = lt("Author: ", "రచయిత: ")
        val readingTime = lt("min read", "నిమిషాల పఠనం")
        val searchTopics = lt("Search topics or articles", "టాపిక్‌లు లేదా కథనాలు వెతకండి")
        val noSearchMatch = lt(
            "No article or topic matches that search.",
            "ఆ శోధనకు సరిపోయే కథనం లేదా టాపిక్ లేదు."
        )
        val noStudyMaterial = lt("No study material yet", "ఇంకా స్టడీ మెటీరియల్ లేదు")
        val noTopicsYet = lt("No topics yet", "ఇంకా టాపిక్‌లు లేవు")
        val markDone = lt("Mark done", "పూర్తయిందని గుర్తించు")
        val marked = lt("Marked", "గుర్తించబడింది")
        val markedCompleted = lt("Marked as completed", "పూర్తయిందని గుర్తించబడింది")
        val markedNotCompleted = lt("Marked as not completed", "పూర్తికాలేదని గుర్తించబడింది")
        val topicsDone = lt("Topics done", "పూర్తయిన టాపిక్‌లు")
        val read = lt("Read", "చదవండి")

        // quizzes & exams
        val quizzes = lt("Quizzes", "క్విజ్‌లు")
        val takeQuiz = lt("Take Quiz", "క్విజ్ రాయండి")
        val startQuiz = lt("Start quiz", "క్విజ్ ప్రారంభించు")
        val startPractice = lt("Start Practice", "ప్రాక్టీస్ ప్రారంభించు")
        val topicQuiz = lt("Topic Quiz", "టాపిక్ క్విజ్")
        val dailyExam = lt("Daily Exam", "రోజువారీ పరీక్ష")
        val dailyExams = lt("Daily Exams", "రోజువారీ పరీక్షలు")
        val dailyPractice = lt("Daily practice", "రోజువారీ ప్రాక్టీస్")
        val grandTest = lt("Grand Test", "గ్రాండ్ టెస్ట్")
        val grandTests = lt("Grand Tests", "గ్రాండ్ టెస్ట్‌లు")
        val weeklyGrandTest = lt("Weekly Grand Test", "వారపు గ్రాండ్ టెస్ట్")
        val todaysExam = lt("Today's Exam", "ఈ రోజు పరీక్ష")
        val startExam = lt("Start Exam", "పరీక్ష ప్రారంభించు")
        val startTheExam = lt("Start the exam", "పరీక్ష ప్రారంభించు")
        val startGrandTest = lt("Start Grand Test", "గ్రాండ్ టెస్ట్ ప్రారంభించు")
        val beforeYouStart = lt("Before you start", "ప్రారంభించే ముందు")
        val timerStartsNote = lt(
            "The timer starts as soon as you tap Start.",
            "స్టార్ట్ నొక్కిన వెంటనే టైమర్ మొదలవుతుంది."
        )
        val noExamScheduled = lt("No exam scheduled right now.", "ఇప్పుడు ఏ పరీక్షా షెడ్యూల్ కాలేదు.")
        val nothingToAttempt = lt("Nothing to attempt", "రాయడానికి ఏమీ లేదు")
        val noQuizForTopic = lt(
            "No quiz available for this topic yet",
            "ఈ టాపిక్‌కు ఇంకా క్విజ్ అందుబాటులో లేదు"
        )
        val noQuizSets = lt("No quiz sets yet", "ఇంకా క్విజ్ సెట్‌లు లేవు")
        val allSets = lt("All sets", "అన్ని సెట్‌లు")
        val backToAllSets = lt("Back to all sets", "అన్ని సెట్‌లకు తిరిగి")
        val retrySet = lt("Retry set", "సెట్ మళ్లీ రాయండి")
        val tryAnotherSet = lt(
            "Try another set from this topic.",
            "ఈ టాపిక్ నుంచి మరో సెట్ ప్రయత్నించండి."
        )
        val practice20 = lt("Practice 20", "20 ప్రాక్టీస్")
        val mixedSubjects = lt("Mixed subjects", "మిశ్రమ సబ్జెక్టులు")
        val singleSubject = lt("Single subject", "ఒకే సబ్జెక్ట్")

        // exam runner
        val question = lt("Question", "ప్రశ్న")
        val questionBank = lt("Question Bank", "ప్రశ్న బ్యాంక్")
        val questionPalette = lt("Question palette", "ప్రశ్నల పాలెట్")
        val questionUnavailable = lt("Question unavailable", "ప్రశ్న అందుబాటులో లేదు")
        val timeLeft = lt("Time left", "మిగిలిన సమయం")
        val markForReview = lt("Mark for review", "సమీక్షకు గుర్తించు")
        val unmarkReview = lt("Unmark review", "సమీక్ష గుర్తు తీసివేయి")
        val clearAnswer = lt("Clear answer", "సమాధానం క్లియర్ చేయి")
        val showAnswer = lt("Show answer", "సమాధానం చూపు")
        val hideAnswer = lt("Hide answer", "సమాధానం దాచు")
        val answerLabel = lt("Answer: ", "సమాధానం: ")
        val submitNow = lt("Submit now?", "ఇప్పుడే సమర్పించాలా?")
        val submitThePaper = lt("Submit the paper?", "పేపర్ సమర్పించాలా?")
        val leaveTheExam = lt("Leave the exam?", "పరీక్ష నుంచి బయటకు వెళ్లాలా?")
        val leave = lt("Leave", "బయటకు")
        val examSubmitted = lt("Exam Submitted 🎉", "పరీక్ష సమర్పించబడింది 🎉")
        val quizCompleted = lt("Quiz Completed 🎉", "క్విజ్ పూర్తయింది 🎉")
        val setComplete = lt("Set complete — ", "సెట్ పూర్తయింది — ")
        val reviewAnswers = lt("Review Answers", "సమాధానాలు సమీక్షించు")
        val answered = lt("Answered", "సమాధానం ఇచ్చినవి")
        val notAnswered = lt("Not answered", "సమాధానం ఇవ్వనివి")
        val notVisited = lt("Not visited", "చూడనివి")
        val correct = lt("Correct", "సరైనవి")
        val wrong = lt("Wrong", "తప్పు")
        val skipped = lt("Skipped", "వదిలేసినవి")
        val nothingToReview = lt("Nothing to review", "సమీక్షించడానికి ఏమీ లేదు")

        // results & analysis
        val result = lt("Result", "ఫలితం")
        val myResults = lt("My results", "నా ఫలితాలు")
        val allResults = lt("All results", "అన్ని ఫలితాలు")
        val recentResults = lt("Recent Results", "ఇటీవలి ఫలితాలు")
        val latestResult = lt("Latest result", "తాజా ఫలితం")
        val resultNotFound = lt("Result not found", "ఫలితం కనబడలేదు")
        val grandTestResult = lt("Grand Test Result", "గ్రాండ్ టెస్ట్ ఫలితం")
        val score = lt("Score", "స్కోరు")
        val bestScore = lt("Best score", "అత్యుత్తమ స్కోరు")
        val averageScore = lt("Average score", "సగటు స్కోరు")
        val accuracy = lt("Accuracy", "కచ్చితత్వం")
        val yourAccuracy = lt("Your accuracy", "మీ కచ్చితత్వం")
        val avgAccuracy = lt("Avg accuracy", "సగటు కచ్చితత్వం")
        val accuracyTrend = lt("Accuracy trend", "కచ్చితత్వ ధోరణి")
        val overall = lt("Overall", "మొత్తం")
        val performance = lt("Performance", "పనితీరు")
        val myPerformance = lt("My Performance", "నా పనితీరు")
        val yourPerformance = lt("Your Performance", "మీ పనితీరు")
        val subjectPerformance = lt("Subject Performance", "సబ్జెక్ట్ పనితీరు")
        val topicPerformance = lt("Topic Performance", "టాపిక్ పనితీరు")
        val fullAnalysis = lt("Full analysis", "పూర్తి విశ్లేషణ")
        val analysis = lt("Analysis", "విశ్లేషణ")
        val analysing = lt("Analysing your attempts…", "మీ ప్రయత్నాలను విశ్లేషిస్తోంది…")
        val attemptHistory = lt("Attempt history", "ప్రయత్నాల చరిత్ర")
        val noAttemptsYet = lt("No attempts yet", "ఇంకా ప్రయత్నాలు లేవు")
        val noDataForSubject = lt("No data for this subject", "ఈ సబ్జెక్ట్‌కు డేటా లేదు")
        val attemptNotInDemo = lt(
            "This attempt is not in the local demo data.",
            "ఈ ప్రయత్నం లోకల్ డెమో డేటాలో లేదు."
        )
        val strongWeakAnalysis = lt("Strong & Weak Analysis", "బలం - బలహీనత విశ్లేషణ")
        val strongSubjects = lt("Strong Subjects 🟢", "బలమైన సబ్జెక్టులు 🟢")
        val weakSubjects = lt("Weak Subjects 🔴", "బలహీన సబ్జెక్టులు 🔴")
        val weakAreas = lt("Weak Areas 🔴", "బలహీన ప్రాంతాలు 🔴")
        val needsPractice = lt("Needs Practice 🟡", "ప్రాక్టీస్ కావాలి 🟡")
        val nothingWeak = lt("Nothing weak right now 🎉", "ఇప్పుడు బలహీనత ఏమీ లేదు 🎉")
        val exploreWeakAreas = lt("Explore Your Weak Areas", "మీ బలహీన ప్రాంతాలను చూడండి")
        val studyWeakTopics = lt("Study Weak Topics", "బలహీన టాపిక్‌లు చదవండి")
        val weakestFirst = lt("Weakest first", "బలహీనమైనవి మొదట")
        val recommendedTopics = lt("Recommended Topics", "సిఫారసు చేసిన టాపిక్‌లు")
        val recommended = lt("Recommended", "సిఫారసు")
        val startWhereItMatters = lt(
            "Start where it matters most",
            "ఎక్కడ ముఖ్యమో అక్కడ మొదలుపెట్టండి"
        )

        // ranks
        val leaderboard = lt("Leaderboard", "లీడర్‌బోర్డ్")
        val ranks = lt("Ranks", "ర్యాంకులు")
        val myRank = lt("My rank", "నా ర్యాంక్")
        val yourRank = lt("Your Rank", "మీ ర్యాంక్")
        val currentRank = lt("Current rank", "ప్రస్తుత ర్యాంక్")
        val rankHash = lt("Rank #", "ర్యాంక్ #")
        val loadingRanks = lt("Loading ranks…", "ర్యాంకులు లోడ్ అవుతున్నాయి…")

        // progress / profile
        val myProgress = lt("My progress", "నా ప్రగతి")
        val yourProgress = lt("Your progress", "మీ ప్రగతి")
        val todaysProgress = lt("Today's Progress", "ఈ రోజు ప్రగతి")
        val studyStreak = lt("Study streak", "చదువు స్ట్రీక్")
        val streak = lt("Streak", "స్ట్రీక్")
        val studyReminders = lt("Study reminders", "చదువు రిమైండర్‌లు")
        val quickLinks = lt("Quick links", "త్వరిత లింక్‌లు")
        val yourDetails = lt("Your details", "మీ వివరాలు")
        val fullName = lt("Full name", "పూర్తి పేరు")
        val enterFullName = lt("Enter your full name", "మీ పూర్తి పేరు నమోదు చేయండి")
        val mobileNumber = lt("Mobile number", "మొబైల్ నంబర్")
        val enterMobile = lt("Enter a 10-digit mobile number", "10 అంకెల మొబైల్ నంబర్ నమోదు చేయండి")
        val usedForRankList = lt("Used only for the rank list", "ర్యాంక్ జాబితా కోసం మాత్రమే")

        // dates / papers
        val importantDates = lt("Important dates", "ముఖ్య తేదీలు")
        val noneAnnounced = lt("None announced", "ప్రకటించలేదు")
        val announcedByDesk = lt("Announced by the desk", "డెస్క్ ప్రకటించింది")
        val chooseExamForDates = lt(
            "Choose an exam to see its dates.",
            "తేదీలు చూడడానికి పరీక్షను ఎంచుకోండి."
        )
        val notScheduled = lt("Not scheduled", "షెడ్యూల్ కాలేదు")
        val previousPapers = lt("Previous papers", "మునుపటి పేపర్లు")
        val pastPapers = lt("Past papers", "గత పేపర్లు")
        val previouslyAsked = lt("Previously asked", "ఇంతకుముందు అడిగినవి")
        val noPastQuestions = lt("No past questions yet", "ఇంకా గత ప్రశ్నలు లేవు")
        val noPastPaperQuestions = lt("No past paper questions", "గత పేపర్ ప్రశ్నలు లేవు")
    }

    /* -------------------------------------------------------- authoring desk */

    object Desk {
        val dashboard = lt("Dashboard", "డాష్‌బోర్డ్")
        val reviewQueue = lt("Review Queue", "సమీక్ష క్యూ")
        val newsManagement = lt("News Management", "వార్తల నిర్వహణ")
        val userManagement = lt("User Management", "వినియోగదారుల నిర్వహణ")
        val roleManagement = lt("Role Management", "రోల్ నిర్వహణ")
        val rolePermissions = lt("Role Permissions", "రోల్ అనుమతులు")
        val systemSettings = lt("System Settings", "సిస్టమ్ సెట్టింగ్‌లు")
        val reporters = lt("Reporters", "రిపోర్టర్లు")
        val aiShorts = lt("AI Shorts", "AI షార్ట్స్")
        val videoTemplates = lt("Video Templates", "వీడియో టెంప్లేట్‌లు")
        val examTypes = lt("Exam Types", "పరీక్ష రకాలు")
        val questionsQuizzes = lt("Questions & Quizzes", "ప్రశ్నలు - క్విజ్‌లు")
        val examResults = lt("Exam Results", "పరీక్ష ఫలితాలు")

        /** Authoring-form chrome for the two language tabs. */
        val languageTabsTitle = lt("Content language", "కంటెంట్ భాష")
        val languageTabsHint = lt(
            "Fill at least one language. The other can be added later — readers fall back to whichever is written.",
            "కనీసం ఒక భాషను నింపండి. మరొకటి తర్వాత చేర్చవచ్చు — రాసిన భాషనే పాఠకులకు చూపుతాము."
        )
        val primaryRequired = lt("At least one language is required", "కనీసం ఒక భాష తప్పనిసరి")
        val teluguEmpty = lt("Telugu version is empty", "తెలుగు వెర్షన్ ఖాళీగా ఉంది")
        val englishEmpty = lt("English version is empty", "ఇంగ్లీష్ వెర్షన్ ఖాళీగా ఉంది")
        val bothFilled = lt("Both languages filled", "రెండు భాషలూ నింపారు")
        val copyToOther = lt("Copy to other language", "మరో భాషకు కాపీ చేయి")
        val onlyInTelugu = lt("Telugu only", "తెలుగు మాత్రమే")
        val onlyInEnglish = lt("English only", "ఇంగ్లీష్ మాత్రమే")
    }
}
