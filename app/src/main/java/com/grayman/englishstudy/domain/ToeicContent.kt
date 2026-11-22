package com.grayman.englishstudy.domain

/**
 * Every TOEIC Speaking practice item, sorted by difficulty. All of it is written for this app;
 * nothing is copied from real tests. Photos are CC0 (assets/toeic/photos/CREDITS.json).
 *
 * BASIC: short texts, one or two people in a photo, everyday topics, simple model answers.
 * MIDDLE: the usual test level: work and travel topics, model answers with reasons and examples.
 * ADVANCED: long texts with numbers and names, busy photos, schedules with changes, abstract opinions.
 */
internal object ToeicContent {
    data class RespondSet(val intro: String, val questions: List<String>, val samples: List<String>)

    data class InfoSet(
        val title: String,
        val rows: List<Pair<String, String>>,
        val note: String,
        val intro: String,
        val questions: List<String>,
        val answers: List<String>,
        val keywords: List<List<String>>,
    )

    data class Opinion(val prompt: String, val sample: String)

    // ---------------------------------------------------------------- Q1-2: read aloud

    val readAloud: Map<ToeicLevel, List<String>> = mapOf(
        ToeicLevel.BASIC to listOf(
            "Welcome to Sunny Market. Today, fresh apples, oranges, and bananas are on sale. Our bakery has warm bread every morning. " +
                "If you need help, please ask any staff member. Thank you for shopping with us.",
            "Hello, this is Tom from City Bank. I'm calling about your new card. It is ready, and you can pick it up at our Main Street office. " +
                "We are open from nine to five. Have a nice day.",
            "Good afternoon, passengers. The next stop is Central Station. Please take all your bags with you. " +
                "If you are going to the airport, please change to the blue line. Thank you for riding with us.",
            "Thank you for visiting Green Park Zoo. The zoo will close in thirty minutes. Please make your way to the main gate. " +
                "Don't forget to visit our gift shop, where you can find toys, books, and snacks.",
            "Here is today's weather. It will be sunny and warm in the morning, but it may rain in the afternoon. " +
                "Please take an umbrella with you. Tomorrow will be cool and windy, with clear skies.",
            "Welcome to our cooking class. Today, we are going to make pasta, salad, and a simple dessert. " +
                "Please wash your hands and put on an apron. If you have any questions, just raise your hand.",
            "Attention, students. The library will close early today because of a staff meeting. " +
                "You can return books at the front desk until four o'clock. The library will open again at nine tomorrow morning.",
            "Hi, this is a message for Ms. Brown. Your package arrived at our store this morning. " +
                "You can pick it up any time before seven p.m. Please bring your receipt or an ID card. Thank you.",
        ),
        ToeicLevel.MIDDLE to listOf(
            "Welcome to Riverside Public Library. Please note that the second floor reading room will be closed this afternoon for maintenance. " +
                "Study rooms on the first floor are still available, and you can reserve one at the front desk. We apologize for any inconvenience.",
            "Thank you for calling Green Valley Dental Clinic. Our office hours are Monday through Friday, from nine a.m. to six p.m. " +
                "If you would like to make, change, or cancel an appointment, please press one. For all other questions, please stay on the line.",
            "Good morning, everyone, and welcome to today's workshop on time management. Over the next two hours, we will look at simple tools, " +
                "practical habits, and realistic goals. Before we begin, please turn off your phones and take a copy of the handout.",
            "Attention, shoppers. For the next thirty minutes, all winter jackets, boots, and scarves are twenty percent off. " +
                "This special discount is available only in our clothing section on the third floor, so don't miss this chance to save.",
            "In local news, the city council has approved a plan to build a new bicycle path along the river. Construction will begin next spring, " +
                "and officials expect the path to reduce traffic, improve air quality, and give residents a safe place to exercise.",
            "Are you looking for a relaxing weekend getaway? Lakeside Resort offers comfortable rooms, delicious local food, and beautiful mountain views. " +
                "Book two nights this month and receive a free dinner for two. Visit our website today for more details.",
            "Thank you for joining tonight's community meeting. We'll start with a short update on the new parking rules, followed by questions from residents. " +
                "After that, our guest speaker, Dr. Helen Ward, will talk about recycling, composting, and reducing waste at home.",
            "Are you tired of long lines at the post office? With our new mobile app, you can print shipping labels, track packages, and schedule pickups from your home. " +
                "Download it today and get free delivery on your first three orders.",
        ),
        ToeicLevel.ADVANCED to listOf(
            "Good evening, and welcome to the annual Westfield Business Awards. Tonight, we will recognize outstanding achievements in innovation, " +
                "customer service, and community leadership. Before we announce the winners, I'd like to thank our sponsors, Harbor Financial, " +
                "Brightline Software, and Maple Street Bakery, whose generous support made this event possible. Please enjoy your dinner, and remember " +
                "that the photo booth near the entrance will remain open until eleven o'clock.",
            "Attention, all passengers on flight two-forty-seven to Vancouver. Due to severe weather conditions, the departure has been delayed by " +
                "approximately ninety minutes. The new boarding time is six fifteen, and boarding will now take place at gate thirty-two instead of " +
                "gate eighteen. Passengers with connecting flights should speak with an agent at the customer service counter. " +
                "We sincerely apologize for the inconvenience and appreciate your patience.",
            "In business news, Northstar Electronics announced on Monday that it will open three new manufacturing facilities in Texas, Ohio, and Georgia. " +
                "The expansion is expected to create more than two thousand jobs over the next five years. Company representatives stated that the decision " +
                "reflects growing demand for energy-efficient appliances, particularly refrigerators, washing machines, and air conditioners. " +
                "Construction on the first facility will begin in early September.",
            "Thank you for calling Pinewood Property Management. If you are a current tenant reporting an emergency, such as a gas leak, flooding, " +
                "or a power outage, please press one immediately. To pay your rent or review your account, press two. For information about available " +
                "apartments, including floor plans, pricing, and parking options, press three. To speak with a representative, please remain on the line.",
            "Are you ready to take your career to the next level? Riverside Community College is now accepting applications for its evening certificate " +
                "programs in accounting, graphic design, and project management. Classes are taught by experienced professionals and are designed for " +
                "working adults. Register before August thirty-first to receive a fifteen percent discount on tuition, and attend our free information " +
                "session next Thursday.",
            "Welcome to the Oakridge Historical Museum. Our permanent collection includes pottery, textiles, and farming tools dating back more than " +
                "three hundred years. On the second floor, you'll find a special exhibition on the history of the local railway, featuring photographs, " +
                "maps, and original tickets. Guided tours depart from the main lobby every hour, and audio guides are available in English, Spanish, and Korean.",
            "This is a reminder for all employees regarding next week's system upgrade. On Saturday, from eight a.m. to approximately four p.m., " +
                "the company's e-mail, payroll, and scheduling systems will be temporarily unavailable. Please save your work and log out of all " +
                "applications before leaving on Friday. If you experience any difficulties after the upgrade, contact the technical support team at extension four-five-two.",
            "And now for the traffic report. Drivers heading downtown should expect significant delays on Highway Nine this morning, where two lanes " +
                "are closed because of road repairs. Commuters are advised to take Lakeview Avenue or Central Boulevard instead. Meanwhile, the Fifth " +
                "Street bridge has reopened after last week's inspection, and public buses are running on their regular schedule.",
        ),
    )

    // ---------------------------------------------------------------- Q3-4: describe a picture

    val pictures: Map<ToeicLevel, List<Photo>> = mapOf(
        ToeicLevel.BASIC to listOf(
            Photo("q01.jpg", "Jeff Sheldon",
                "This picture was taken in a cafe. An older man with gray hair is sitting at a long wooden table by the window. He is wearing a brown sweater, and there is a cup of coffee in front of him. Lamps are hanging from the ceiling, and I can see shops across the street.",
                listOf("cafe", "man", "sitting", "table", "window", "coffee", "sweater", "lamps")),
            Photo("q02.jpg", "Burst",
                "In this picture, a young woman is reading a book. She is sitting on the floor next to a sofa with many pillows. She has long black hair and is wearing an orange sweater and a long blue skirt. There is a green plant behind her.",
                listOf("woman", "reading", "book", "sitting", "sofa", "pillows", "sweater", "plant")),
            Photo("q03.jpg", "Skitter Photo",
                "This picture shows a man riding a bicycle. He is wearing a black and white cycling shirt and black shorts. He is riding on a road next to a large green field. The sky is blue with many clouds, and he looks like he is moving fast.",
                listOf("man", "riding", "bicycle", "road", "field", "shirt", "sky", "clouds")),
            Photo("q04.jpg", "Matt Moloney",
                "In this picture, a woman is standing in a high place and looking at the city. She has long brown hair and is wearing blue jeans. She is carrying a white shopping bag. There is a tall fence in front of her, and the sky is blue.",
                listOf("woman", "standing", "looking", "city", "fence", "jeans", "bag", "sky")),
            Photo("q05.jpg", "Toa Heftiba",
                "This picture was taken in a kitchen. A cook is cutting vegetables on a green cutting board. He is wearing a striped hat, a white shirt and a blue striped apron. Behind him, there are ovens, pots and a blue trash can.",
                listOf("kitchen", "cook", "cutting", "knife", "vegetables", "board", "apron", "hat")),
            Photo("q06.jpg", "Natalya Zaritskaya",
                "This picture shows a family at the beach. A man, a woman and a little girl are holding hands and standing in the water. The waves are coming in, and the sand is wet. The sky is blue with white clouds, and it looks like a sunny day.",
                listOf("family", "beach", "holding", "hands", "water", "waves", "sand", "sunny")),
            Photo("q07.jpg", "Olu Eletu",
                "In this picture, a man is looking at a tablet. He is holding a pen and pointing at a chart on the screen. He is wearing a striped shirt, dark pants and brown shoes, and his legs are crossed. He might be checking some business data.",
                listOf("man", "tablet", "chart", "pen", "shirt", "sitting", "shoes", "checking")),
            Photo("q08.jpg", "Josh Willink",
                "This picture was taken outside near some trees. A woman is kneeling on the ground and hugging a little child. She is also carrying a baby on her chest. The woman is wearing a red hat, and the child is wearing a gray knit hat and a light blue jacket.",
                listOf("woman", "hugging", "child", "baby", "hat", "kneeling", "trees", "jacket")),
            Photo("q09.jpg", "Alex Jones",
                "In this picture, a young man is walking on a path in a park. He is carrying a backpack and wearing a red cap. It is very foggy, so the trees in the background are hard to see. There are street lamps along the path and grass on both sides.",
                listOf("man", "walking", "path", "backpack", "cap", "foggy", "lamps", "grass")),
            Photo("q10.jpg", "Agnieszka Walędziak",
                "This picture shows fruit and vegetables at a market. There are many apples on the top shelf. Below them, there are red and yellow peppers, oranges and green apples in blue baskets. At the bottom, I can see some carrots. Each basket has a small price tag.",
                listOf("market", "fruit", "vegetables", "apples", "peppers", "baskets", "oranges", "price")),
            Photo("p05.jpg", "Startup Stock Photos",
                "In this picture, a young man is standing in front of a large whiteboard. He is wearing a blue T-shirt and a dark red knit hat. He is drawing a chart with a marker. There are many boxes, arrows and notes on the whiteboard.",
                listOf("whiteboard", "standing", "writing", "drawing", "marker", "shirt", "hat", "chart")),
            Photo("p83.jpg", "Startup Stock Photos",
                "This picture shows a woman working on a laptop. She has long brown hair and is wearing a light sweater. She is sitting on a wooden bench next to a brick wall, with the laptop on her lap. Behind her, there are two windows.",
                listOf("woman", "laptop", "typing", "sitting", "bench", "brick", "wall", "windows")),
            Photo("p12.jpg", "Bruce Mars",
                "This picture was taken at a train station. A man with glasses and a beard is getting on a train. He is holding the handrail by the door and smiling. He is wearing a long beige coat. The platform behind him is almost empty.",
                listOf("train", "station", "platform", "man", "coat", "smiling", "holding", "door")),
        ),
        ToeicLevel.MIDDLE to listOf(
            Photo("q11.jpg", "Christina Morillo",
                "This picture was taken in an office with a large window. Two women are sitting at a small white round table and talking. The woman on the left has long hair and is smiling while she explains something with her hands. The woman on the right has curly hair and is listening with her hands together. Outside the window, I can see many city buildings.",
                listOf("women", "talking", "table", "window", "smiling", "listening", "office", "buildings")),
            Photo("q12.jpg", "David Marcu",
                "In this picture, two women are riding bicycles side by side on a city street. Both of them are wearing black jackets, and one bike has a basket on the front. The background is blurry, so they seem to be moving quickly. Behind them, there is a red brick building with large doors.",
                listOf("women", "riding", "bicycles", "street", "jackets", "basket", "building", "moving")),
            Photo("q13.jpg", "Helena Lopes",
                "This picture shows a group of young people relaxing in a living room. In the front, a man with glasses is reading a magazine full of photos. Next to him, a woman is looking at her smartphone. Behind them, other people are sitting on a sofa and chatting. It looks like a casual get-together among friends.",
                listOf("people", "magazine", "reading", "smartphone", "sofa", "relaxing", "glasses", "friends")),
            Photo("q14.jpg", "Michael Mongin",
                "This picture was taken at a concert. Two musicians are performing on a stage. On the left, a man is playing an electric guitar, and on the right, another man is playing the drums. Bright purple and white lights are shining from behind them, and there is a metal frame for the stage lights.",
                listOf("concert", "stage", "musicians", "guitar", "drums", "playing", "lights", "performing")),
            Photo("q15.jpg", "Matthias Zomer",
                "This picture shows a large group of sheep standing in a field. Most of the sheep are white with black faces, and some of them are looking at the camera. The grass is dry and yellow. In the background, there are some trees, and I can see a person far away, maybe a farmer. The sky is cloudy.",
                listOf("sheep", "field", "grass", "standing", "trees", "farmer", "cloudy", "group")),
            Photo("q16.jpg", "Startup Stock Photos",
                "This picture shows some people working together at a table. In the front, a man in a red T-shirt is holding a small piece of paper. On the table, there are many colorful sticky notes, large sheets of paper, a marker and a bottle of water. Other people are reaching across the table, so they seem to be organizing ideas.",
                listOf("table", "notes", "paper", "marker", "bottle", "shirt", "working", "ideas")),
            Photo("q17.jpg", "Startup Stock Photos",
                "This picture was taken in a meeting room. Several laptops are open on a long light-colored table. A man in a light gray T-shirt is sitting with his hands together. There is a smartphone and a red marker on the table. Behind the laptops, I can see two other people wearing name tags around their necks.",
                listOf("meeting", "laptops", "table", "smartphone", "marker", "sitting", "room", "people")),
            Photo("q18.jpg", "Helena Lopes",
                "In this picture, four young friends are standing outside and laughing together. On the left, a man with glasses is wearing a light blue shirt. Next to him, a woman in sunglasses and a denim jacket is smiling. On the right, two men are joking, and one of them is raising his hands. The sun is shining brightly behind them.",
                listOf("friends", "laughing", "standing", "sunglasses", "glasses", "jacket", "smiling", "outside")),
            Photo("q19.jpg", "Leeroy",
                "This picture was taken from outside a restaurant, through a large window. A woman with long blond hair is sitting at a table. She is wearing a blue denim shirt and looking down, maybe at her phone or a menu. There are wine glasses on the tables, and many light bulbs are hanging from the ceiling inside.",
                listOf("restaurant", "window", "woman", "sitting", "glasses", "hair", "shirt", "lights")),
            Photo("q20.jpg", "Negative Space",
                "This picture shows the inside of a bus. There is a narrow aisle in the middle, with purple seats on both sides. Some passengers are sitting and looking forward. There are green poles with yellow stop buttons next to the seats, and at the front of the bus, there is a large window.",
                listOf("bus", "inside", "seats", "aisle", "passengers", "poles", "buttons", "window")),
            Photo("p00.jpg", "Startup Stock Photos",
                "This picture shows a small team meeting around a wooden table. On the right, a man with glasses is typing on a laptop, and the others are leaning on the table with notebooks open in front of them. There are glasses of iced tea, a pair of reading glasses and some papers on the table. It looks like they are discussing a project together.",
                listOf("meeting", "table", "laptop", "notebook", "glasses", "drink", "discussing", "typing")),
            Photo("p15.jpg", "rawpixel (public domain)",
                "In this picture, an elderly couple is standing on a train platform, holding hands. The woman is wearing a blue jacket and a hat, and the man is wearing a dark suit. They are waiting in front of the platform safety doors. In the background, I can see tall office buildings and a clear blue sky.",
                listOf("couple", "elderly", "platform", "holding", "hands", "waiting", "buildings", "jacket")),
            Photo("p34.jpg", "Pj Accetturo",
                "This picture shows the inside of a bookstore. In the middle, a man in a brown leather jacket is holding his phone up high, maybe to take a picture of the shelves. Behind him, a woman is standing at the counter and looking at a book. The shelves are full of colorful books, and there are piles of books on a table on the right.",
                listOf("bookstore", "books", "shelves", "phone", "jacket", "counter", "woman", "picture")),
            Photo("p41.jpg", "Matt Moloney",
                "This picture was taken in an airport walkway. Two men with backpacks are walking away from the camera, and one of them is pulling a suitcase. There are moving walkways on both sides, and large windows along the wall. It looks like they are heading to their gate.",
                listOf("airport", "walkway", "walking", "backpack", "suitcase", "pulling", "windows", "men")),
            Photo("p72.jpg", "Austin Ban",
                "In this picture, a group of hikers is walking along a narrow trail through a grassy field. They are carrying large backpacks, and one of them is wearing a hat. In the distance, there is a thick forest and mountains under a partly cloudy sky. It looks like a sunny summer day for a long hike.",
                listOf("hikers", "hiking", "trail", "backpacks", "field", "forest", "mountains", "walking")),
        ),
        ToeicLevel.ADVANCED to listOf(
            Photo("q21.jpg", "Leeroy",
                "This picture was taken in a busy wine bar. Several people are sitting on high stools around large wooden barrels that are being used as tables. On the barrels, there are glasses of wine and beer, along with some plates of food. Along the back wall, wooden shelves are filled with wine bottles. A few people in the foreground are blurry, which suggests that they are moving, so the place seems lively and crowded.",
                listOf("bar", "barrels", "stools", "wine", "beer", "shelves", "bottles", "crowded")),
            Photo("q22.jpg", "Matt Moloney",
                "This picture shows a group of tourists on an observation deck, looking out over a city at sunset. Most of them have their backs to the camera and are leaning against a tall metal fence. A woman on the left is taking a picture with her phone, and there is a young boy in shorts in the middle. Several people are carrying shoulder bags, and someone has a jacket tied around their waist. In the distance, tall buildings rise along the horizon.",
                listOf("tourists", "observation", "city", "sunset", "fence", "phone", "bags", "buildings")),
            Photo("q23.jpg", "Brodie Vissers",
                "This picture shows a group of young people working on a project together. In the foreground, a woman with a red scarf and a pencil in her hair is drawing on a large sheet of paper with colored markers. Two other people are sitting on the edge of the table, and one of them is holding a smartphone. A man on the left has headphones around his neck. Markers, a cup and a phone are scattered across the table, so it looks like a brainstorming session.",
                listOf("project", "drawing", "paper", "markers", "scarf", "headphones", "smartphone", "brainstorming")),
            Photo("q24.jpg", "Vladimir Kudinov",
                "This picture was taken on a farm in the countryside. Two horses are pulling a large orange machine across a field, and a man in a hat is sitting on a seat behind them. The machine seems to be making round bales of hay, and one large bale is lying on the ground on the left. Two people are standing next to it and watching. Rows of cut grass cover the field, and the sky is gray and overcast.",
                listOf("farm", "horses", "pulling", "machine", "hay", "field", "watching", "countryside")),
            Photo("q25.jpg", "Daryan Shamkhali",
                "This picture shows a wide street in a big city, taken from the middle of the road. Traffic is moving away from the camera, and I can see a police car parked on the left and several yellow taxis further ahead. Tall office buildings line both sides of the street, and there are shops with signs on the ground floor. The foreground of the road is empty, and metal barriers run along the sidewalk on the left.",
                listOf("street", "city", "traffic", "taxis", "police", "buildings", "shops", "barriers")),
            Photo("q26.jpg", "贝莉儿 NG",
                "This picture shows a tall building under construction. Three tower cranes, two yellow ones and a red one, are standing on top of the building. The upper floors are surrounded by metal scaffolding and white safety netting. A large flock of birds is flying across the pale blue sky above the cranes. Since the outer walls haven't been finished yet, the project seems to have a long way to go.",
                listOf("building", "construction", "cranes", "scaffolding", "netting", "birds", "sky", "floors")),
            Photo("q27.jpg", "Ksenia Kudelkina",
                "This picture was taken at a harbor in the early evening. In the foreground, there is a large floating crane on a rusty barge. The crane is painted blue and yellow, and the number 415 is written on its side. Old tires are hanging along the edge of the barge. The water is very calm, and there are pieces of ice floating on it. On the far side of the water, I can see a city skyline.",
                listOf("harbor", "crane", "barge", "tires", "water", "ice", "skyline", "evening")),
            Photo("q28.jpg", "Daria Nepriakhina",
                "This picture shows a large conference hall with a high, decorated ceiling. Rows of red chairs fill the room, but only a few people are sitting in them, so the event may not have started yet, or it could be a break. Some people are standing and talking near the back. Blue lights are shining on the wall, and the whole room has a warm orange glow.",
                listOf("conference", "hall", "chairs", "rows", "sitting", "lights", "ceiling", "event")),
            Photo("q29.jpg", "Timur Saglambilek",
                "This picture was taken in a busy restaurant kitchen. In the foreground, there are two large metal pots full of vegetables and meat, and a lot of steam is rising from them. On the left, a cook in a white jacket and a dark apron is looking down, probably checking the food. In the background, several small lights are hanging from the ceiling, and there are windows on the far wall.",
                listOf("kitchen", "pots", "steam", "vegetables", "cook", "apron", "lights", "cooking")),
            Photo("q30.jpg", "Jay Mantri",
                "This picture shows a beautiful bay seen from a hill. Dozens of white boats and yachts are anchored in the calm blue water, and one boat is leaving a curved trail as it moves. On the right, a green hill rises from the sea, and there is a pier with some buildings along the shore. In the foreground, I can see the tops of some trees. The sky is a little hazy, but it looks like a pleasant day.",
                listOf("bay", "boats", "yachts", "water", "hill", "pier", "shore", "trees")),
            Photo("p43.jpg", "rawpixel (public domain)",
                "This picture shows a very busy airport terminal. Lots of travelers are walking around with suitcases and bags. In the front, there are two signs: a blue one for information and a red one pointing to shops, restaurants and restrooms. Near the middle, two police officers are standing, and there is a departure board on the left.",
                listOf("airport", "busy", "travelers", "suitcase", "sign", "information", "police", "crowd")),
            Photo("p47.jpg", "Michael Browning",
                "This picture was taken in a restaurant kitchen. In the front, a man in a white shirt and apron is standing with his back to the camera. Across the counter, another cook in a white hat is busy preparing food. Several metal lamps are hanging from the ceiling, and there are stacks of white plates on the shelves.",
                listOf("kitchen", "restaurant", "cook", "chef", "apron", "lamps", "plates", "preparing")),
            Photo("p53.jpg", "rawpixel (public domain)",
                "This picture shows people crossing the street at a crosswalk. They seem to be in a hurry, because they are walking quickly and the picture is a little blurry. In the front, a man in a gray jacket and dark pants is taking a big step, and a woman next to him is carrying a brown handbag. It looks like a busy morning in the city.",
                listOf("crossing", "street", "crosswalk", "people", "hurry", "walking", "jacket", "handbag")),
            Photo("p89.jpg", "rawpixel (public domain)",
                "This picture was taken at an outdoor festival or street performance. In the middle, a man in a white hat and dark poncho is playing the pan flute. On the left, someone is playing a violin, and there is another man in a gray hat on the right. Behind them, a large crowd of people is gathered in front of an old building.",
                listOf("music", "playing", "flute", "hat", "violin", "crowd", "festival", "performance")),
        ),
    )

    // ---------------------------------------------------------------- Q5-7: respond to questions

    val respondSets: Map<ToeicLevel, List<RespondSet>> = mapOf(
        ToeicLevel.BASIC to listOf(
            RespondSet(
                "Imagine that a British marketing company is doing research in your area. You have agreed to participate in a telephone interview about breakfast.",
                listOf("What time do you usually eat breakfast?", "What do you usually have for breakfast?", "Do you think breakfast is important? Why or why not?"),
                listOf(
                    "I usually eat breakfast at seven thirty, before I go to work.",
                    "I usually have toast and a cup of coffee. Sometimes I eat rice and soup.",
                    "Yes, I think breakfast is important. It gives me energy in the morning, and I can focus better at work. Also, if I skip breakfast, I eat too much at lunch.",
                ),
            ),
            RespondSet(
                "Imagine that a friend is going to visit your town. You are talking on the telephone about weekend activities.",
                listOf("What do you usually do on weekends?", "Who do you usually spend your weekends with?", "If I visit your town on the weekend, where should I go, and why?"),
                listOf(
                    "On weekends, I usually rest at home and watch movies.",
                    "I usually spend my weekends with my family.",
                    "You should go to the river park. It is beautiful, and you can ride a bike there. There are also many good restaurants nearby, so we can have lunch together.",
                ),
            ),
            RespondSet(
                "Imagine that a music magazine is doing research. You have agreed to answer some questions about music.",
                listOf("How often do you listen to music?", "What kind of music do you like?", "Where do you usually listen to music, and why?"),
                listOf(
                    "I listen to music every day, usually on my way to work.",
                    "I like pop music because it is fun and easy to sing.",
                    "I usually listen to music on the subway. The trip is long, so music makes it more fun, and I don't feel bored. I also listen to music when I exercise.",
                ),
            ),
            RespondSet(
                "Imagine that a clothing company is doing research in your country. You are participating in a telephone interview about shopping for clothes.",
                listOf("When was the last time you bought clothes?", "Do you like to shop for clothes alone or with friends?", "Do you prefer to buy clothes online or in a store? Why?"),
                listOf(
                    "I bought clothes last month. I bought a warm jacket.",
                    "I like to shop alone, because I can take my time.",
                    "I prefer to buy clothes in a store, because I can try them on. When I buy clothes online, sometimes the size or color is different, and returning them is not easy.",
                ),
            ),
            RespondSet(
                "Imagine that a pet food company is doing research. You have agreed to answer some questions about pets.",
                listOf("Do you have a pet now, or did you have one when you were young?", "What animal do you think makes the best pet?", "Is it a good idea for children to have pets? Why or why not?"),
                listOf(
                    "Yes, I have a small dog. Her name is Coco.",
                    "I think dogs are the best pets, because they are friendly and loyal.",
                    "Yes, I think it is a good idea. Children can learn to take care of something, like feeding and walking a dog. Also, pets make children happy and less lonely.",
                ),
            ),
            RespondSet(
                "Imagine that a travel company is doing research. You are participating in a telephone interview about seasons and weather.",
                listOf("What is your favorite season?", "What do you usually do in that season?", "Which season is the best time for tourists to visit your country? Why?"),
                listOf(
                    "My favorite season is autumn.",
                    "In autumn, I usually go hiking with my friends.",
                    "I think autumn is the best time. The weather is cool and sunny, so it is good for walking around. Also, the mountains turn red and yellow, so they are very beautiful.",
                ),
            ),
        ),
        ToeicLevel.MIDDLE to listOf(
            RespondSet(
                "Imagine that a marketing firm is doing research in your country. You have agreed to participate in a telephone interview about coffee shops.",
                listOf(
                    "How often do you go to a coffee shop, and who do you usually go with?",
                    "What do you usually order at a coffee shop?",
                    "What is the most important thing for you when choosing a coffee shop, and why?",
                ),
                listOf(
                    "I go to a coffee shop about three times a week, usually with my coworkers after lunch.",
                    "I usually order an iced americano because it's refreshing and not too sweet.",
                    "The most important thing for me is a quiet atmosphere, because I often read or work there. Comfortable seats and good Wi-Fi also matter, so I can stay for a few hours.",
                ),
            ),
            RespondSet(
                "Imagine that a friend is planning to move to your city. You are talking on the telephone about public transportation.",
                listOf(
                    "What kind of public transportation do you use most often?",
                    "How long does it take you to get to work or school?",
                    "What advice would you give someone who is new to public transportation in your city?",
                ),
                listOf(
                    "I use the subway most often because it's fast and always on time.",
                    "It takes about forty minutes to get to work, including a short walk.",
                    "I'd recommend getting a transportation card first, because you can use it on buses and subways and transfers are cheaper. Also, try to avoid rush hour if you can.",
                ),
            ),
            RespondSet(
                "Imagine that a Canadian travel magazine is doing research. You have agreed to answer some questions about vacations.",
                listOf(
                    "When was the last time you went on a vacation?",
                    "Do you prefer to travel alone or with others? Why?",
                    "Describe a place you would recommend to a tourist visiting your country.",
                ),
                listOf(
                    "The last time I went on vacation was last summer, when I went to Jeju Island for four days.",
                    "I prefer to travel with friends because it's more fun to share the experience, and it's cheaper.",
                    "I'd recommend Gyeongju. It has many historic sites, beautiful temples and traditional houses, and you can rent a bike to look around the city easily.",
                ),
            ),
            RespondSet(
                "Imagine that a technology company is doing research. You are participating in a telephone interview about smartphones.",
                listOf(
                    "How many hours a day do you use your smartphone?",
                    "What app do you use the most, and why?",
                    "Do you think people spend too much time on their phones? Why or why not?",
                ),
                listOf(
                    "I use my smartphone about four hours a day, mostly in the evening.",
                    "I use a messaging app the most, because I talk with my family and coworkers on it every day.",
                    "Yes, I think so. Many people check their phones even while talking with friends or eating, and it can hurt their sleep and relationships. Setting time limits could help.",
                ),
            ),
            RespondSet(
                "Imagine that a local newspaper is writing about exercise. You have agreed to answer some questions.",
                listOf(
                    "What kind of exercise do you enjoy?",
                    "Where do you usually exercise?",
                    "What would encourage more people in your area to exercise regularly?",
                ),
                listOf(
                    "I enjoy jogging and sometimes swimming.",
                    "I usually exercise at a park near my apartment, early in the morning.",
                    "I think more free public facilities would help, like outdoor gyms and walking paths. Group classes after work could also motivate people, because it's easier to keep going with others.",
                ),
            ),
            RespondSet(
                "Imagine that an online education company is doing research. You have agreed to participate in a telephone interview about learning new skills.",
                listOf(
                    "When was the last time you learned something new, and what was it?",
                    "Do you prefer to learn online or in a classroom? Why?",
                    "What would you consider when choosing a class to learn a new skill? Why?",
                ),
                listOf(
                    "The last time was a few months ago, when I started learning how to swim.",
                    "I prefer learning online, because I can study whenever I have free time, even late at night.",
                    "I would consider the teacher's experience and reviews from other students. If the teacher is skilled and the reviews are positive, I can be sure the class is worth my money and time.",
                ),
            ),
        ),
        ToeicLevel.ADVANCED to listOf(
            RespondSet(
                "Imagine that a research firm is conducting a study about workplaces. You have agreed to participate in a telephone interview about working environments.",
                listOf(
                    "How many people do you usually work with, and what do you do together?",
                    "Would you rather have a private office or work in an open space? Why?",
                    "What are the advantages and disadvantages of having flexible working hours?",
                ),
                listOf(
                    "I usually work with a team of about six people. We plan projects together and share updates every morning.",
                    "I'd rather have a private office, because I can concentrate without interruptions, especially when I'm working on detailed reports.",
                    "The biggest advantage is that employees can work when they're most productive and balance their personal lives more easily. On the other hand, it can be harder to schedule meetings, and some people may feel less connected to their team, so clear communication rules are essential.",
                ),
            ),
            RespondSet(
                "Imagine that a city government is doing research. You have agreed to participate in a telephone interview about your neighborhood.",
                listOf(
                    "How long have you lived in your neighborhood, and how has it changed?",
                    "What is one facility your neighborhood needs, and why?",
                    "If the city could improve only one thing in your area, should it be parks, public transportation, or libraries? Why?",
                ),
                listOf(
                    "I've lived here for about eight years, and in that time, many new apartment buildings and cafes have opened.",
                    "I think we need a public sports center, because the nearest gym is quite expensive and far away.",
                    "I would choose public transportation. Right now, buses are crowded during rush hour and don't run late at night. Better transportation would reduce traffic and pollution, and it would help everyone, from students to elderly people, get around more easily.",
                ),
            ),
            RespondSet(
                "Imagine that a technology magazine is doing research. You are participating in a telephone interview about artificial intelligence.",
                listOf(
                    "How often do you use AI tools, and what do you use them for?",
                    "Do you think AI tools will change the way people study? How?",
                    "Some people worry that AI will replace many jobs. What is your opinion?",
                ),
                listOf(
                    "I use AI tools almost every day, mainly to translate documents and summarize long articles.",
                    "Yes, definitely. Students can get instant explanations and personalized practice, so studying will become much more efficient.",
                    "I think AI will replace some repetitive tasks, but it will also create new jobs, such as managing and checking AI systems. The key is for workers to keep learning new skills, and for companies and governments to provide retraining programs.",
                ),
            ),
            RespondSet(
                "Imagine that a health organization is conducting research. You have agreed to answer some questions about stress.",
                listOf(
                    "When do you usually feel the most stressed?",
                    "What do you do to relieve stress?",
                    "Should companies be responsible for reducing their employees' stress? Why or why not?",
                ),
                listOf(
                    "I usually feel the most stressed at the end of the month, when several deadlines come at once.",
                    "I go for a long walk or play badminton, because exercise clears my mind.",
                    "I believe companies share some of the responsibility. Stressed employees make more mistakes and are more likely to quit, so it's in the company's own interest. Simple measures, like reasonable workloads, flexible schedules, and access to counseling, can make a big difference.",
                ),
            ),
            RespondSet(
                "Imagine that a marketing company is doing research. You are participating in a telephone interview about online reviews.",
                listOf(
                    "How often do you read online reviews before buying something?",
                    "Have you ever written an online review? Why or why not?",
                    "How much do you trust online reviews when you make an important purchase? Explain.",
                ),
                listOf(
                    "I read online reviews almost every time I buy something, especially electronics.",
                    "Yes, I have. I wrote a review for a restaurant because the service was excellent, and I wanted to help other customers.",
                    "I trust them to some extent, but not completely. Some reviews are fake or written by people who were paid. So for important purchases, I compare several websites, look for detailed reviews with photos, and also ask friends who have used the product.",
                ),
            ),
            RespondSet(
                "Imagine that a travel agency is doing research. You have agreed to participate in a telephone interview about business travel.",
                listOf(
                    "Have you ever traveled for work or school? Where did you go?",
                    "What is the most difficult thing about traveling for work?",
                    "Do you think video conferencing can replace business trips? Why or why not?",
                ),
                listOf(
                    "Yes, last year I went to Singapore for a three-day conference.",
                    "The hardest thing is the tight schedule, because you have little time to rest or recover from jet lag.",
                    "It can replace many trips, especially routine meetings, which saves time and money. However, for important negotiations or building new relationships, meeting in person is still more effective, because people build trust more easily face to face.",
                ),
            ),
        ),
    )

    // ---------------------------------------------------------------- Q8-10: respond using information

    val infoSets: Map<ToeicLevel, List<InfoSet>> = mapOf(
        ToeicLevel.BASIC to listOf(
            InfoSet(
                "Yoga for Beginners — Sunnyside Community Center",
                listOf(
                    "Monday 7:00 – 8:00 p.m." to "Basic stretching (Room A)",
                    "Wednesday 7:00 – 8:00 p.m." to "Breathing and balance (Room A)",
                    "Saturday 10:00 – 11:00 a.m." to "Yoga in the park (Green Park)",
                ),
                "Fee: \$40 a month · Please bring your own mat",
                "Hi, I'm thinking about joining the yoga class. Can I ask you some questions?",
                listOf(
                    "Where is the class on Monday, and what time does it start?",
                    "I don't have a yoga mat. Can I borrow one at the class?",
                    "Can you tell me about the classes on the other days?",
                ),
                listOf(
                    "The Monday class is in Room A at the community center, and it starts at seven p.m.",
                    "I'm sorry, but you need to bring your own mat.",
                    "Sure. On Wednesday, from seven to eight p.m., there is a breathing and balance class in Room A. On Saturday, from ten to eleven a.m., there is yoga in Green Park.",
                ),
                listOf(listOf("seven", "room"), listOf("bring", "own"), listOf("wednesday", "balance", "saturday", "park")),
            ),
            InfoSet(
                "Mia's Birthday Party — Saturday, May 10",
                listOf(
                    "12:00 p.m." to "Lunch at Tony's Pizza (Main Street)",
                    "1:30 p.m." to "Movie: The Space Cats (City Cinema)",
                    "4:00 p.m." to "Cake and games at Mia's house",
                ),
                "Please bring a small gift (under \$20)",
                "Hi, it's Jake. I'm coming to Mia's birthday party, but I forgot the details.",
                listOf(
                    "Where do we meet, and what time?",
                    "We are going to watch a movie at Mia's house, right?",
                    "What are we doing after lunch?",
                ),
                listOf(
                    "We meet at Tony's Pizza on Main Street at twelve p.m.",
                    "No, we are watching the movie at City Cinema at one thirty. We go to Mia's house later.",
                    "After lunch, we watch the movie The Space Cats at City Cinema at one thirty. Then at four p.m., we have cake and play games at Mia's house.",
                ),
                listOf(listOf("pizza", "twelve"), listOf("cinema", "thirty"), listOf("movie", "cake", "games")),
            ),
            InfoSet(
                "Hilltop Hotel — Guest Information",
                listOf(
                    "Breakfast" to "7:00 – 10:00 a.m. (restaurant, 1st floor)",
                    "Swimming pool" to "8:00 a.m. – 9:00 p.m. (3rd floor)",
                    "Check-out" to "11:00 a.m.",
                    "Airport bus" to "Every hour from 6:00 a.m. (\$5)",
                ),
                "Free Wi-Fi in all rooms",
                "Hello, I'm staying at your hotel, and I have a few questions.",
                listOf(
                    "What time is breakfast, and where is it?",
                    "Check-out is at twelve o'clock, right?",
                    "Can you tell me about the swimming pool and the airport bus?",
                ),
                listOf(
                    "Breakfast is from seven to ten a.m. in the restaurant on the first floor.",
                    "Actually, check-out is at eleven a.m.",
                    "Sure. The swimming pool is on the third floor, and it's open from eight a.m. to nine p.m. The airport bus leaves every hour from six a.m., and it costs five dollars.",
                ),
                listOf(listOf("seven", "first"), listOf("eleven"), listOf("third", "hour", "five")),
            ),
            InfoSet(
                "Riverside Library — Kids' Week",
                listOf(
                    "Monday 3:00 p.m." to "Story time (ages 4–7)",
                    "Tuesday 4:00 p.m." to "Drawing class (ages 6–10)",
                    "Thursday 3:30 p.m." to "Science fun (ages 8–12)",
                    "Saturday 11:00 a.m." to "Movie day (all ages)",
                ),
                "All events are free · Sign up at the front desk",
                "Hi, I have two children, and I'd like to know about Kids' Week at the library.",
                listOf(
                    "How much do the events cost?",
                    "My son is seven. Can he join the science event?",
                    "My daughter is five. Which events can she join?",
                ),
                listOf(
                    "All the events are free, but you need to sign up at the front desk.",
                    "I'm sorry, but the science event is for ages eight to twelve. He can join the drawing class on Tuesday at four p.m.",
                    "Your daughter can join story time on Monday at three p.m., because it is for ages four to seven. She can also come to movie day on Saturday at eleven a.m. It is for all ages.",
                ),
                listOf(listOf("free", "sign"), listOf("eight", "drawing"), listOf("story", "monday", "movie", "saturday")),
            ),
        ),
        ToeicLevel.MIDDLE to listOf(
            InfoSet(
                "Business Writing Workshop — Saturday, June 14, Room 204",
                listOf(
                    "9:00 – 9:30" to "Registration and coffee",
                    "9:30 – 10:30" to "Writing clear e-mails (Laura Kim)",
                    "10:45 – 11:45" to "Reports that get read (Daniel Ortiz)",
                    "12:00 – 1:00" to "Lunch (included)",
                    "1:00 – 2:30" to "Workshop: Editing your own writing (Laura Kim)",
                ),
                "Fee: \$80 · Early registration (by June 1): \$65",
                "Hi, I'm interested in the business writing workshop, but I lost the information sheet. Could you answer a few questions?",
                listOf(
                    "What time does the workshop start, and where is it?",
                    "I heard that lunch is not included and I need to bring my own. Is that right?",
                    "Could you tell me about the sessions that Laura Kim is leading?",
                ),
                listOf(
                    "The workshop starts at nine o'clock with registration and coffee, and it's in Room 204.",
                    "Actually, lunch is included, so you don't need to bring your own. It's from twelve to one.",
                    "Sure. Laura Kim leads two sessions. First, from nine thirty to ten thirty, she talks about writing clear e-mails. Then after lunch, from one to two thirty, she runs a workshop on editing your own writing.",
                ),
                listOf(listOf("nine", "204"), listOf("included", "twelve"), listOf("e-mails", "editing", "one")),
            ),
            InfoSet(
                "Dr. Patel — Tuesday Schedule",
                listOf(
                    "8:30 a.m." to "Staff meeting",
                    "10:00 a.m." to "Patient: Mr. Green (check-up)",
                    "11:30 a.m." to "Patient: Ms. Lopez (follow-up)",
                    "1:00 p.m." to "Lunch with hospital director",
                    "3:00 p.m." to "Patient: Mr. Wu (new patient)",
                ),
                "Clinic closes at 5:00 p.m.",
                "Hello, this is Dr. Patel. I'm on my way to the clinic and I need to check my schedule for today.",
                listOf(
                    "What is the first thing on my schedule this morning?",
                    "My lunch is with a new patient, right?",
                    "Can you tell me about all of my patient appointments for today?",
                ),
                listOf(
                    "Your first event is a staff meeting at eight thirty in the morning.",
                    "No, actually your lunch at one p.m. is with the hospital director. Your new patient, Mr. Wu, is at three p.m.",
                    "You have three patients today. Mr. Green has a check-up at ten, Ms. Lopez has a follow-up at eleven thirty, and Mr. Wu, a new patient, comes at three p.m.",
                ),
                listOf(listOf("staff", "meeting"), listOf("director", "wu"), listOf("green", "lopez", "wu")),
            ),
            InfoSet(
                "City Food Festival — Riverside Park",
                listOf(
                    "Friday 5–10 p.m." to "Opening night: street food & live jazz",
                    "Saturday 11 a.m.–9 p.m." to "Cooking contest, kids' baking class (2 p.m.)",
                    "Sunday 11 a.m.–6 p.m." to "Farmers' market, dessert tasting",
                ),
                "Tickets: \$10 per day · Children under 12: free · Parking at City Hall",
                "Hi, I saw a poster for the food festival, and I have a few questions.",
                listOf(
                    "Where is the festival, and how much are tickets?",
                    "I want to bring my kids. Do they need tickets, and is there anything for them?",
                    "What can I do at the festival on the weekend?",
                ),
                listOf(
                    "The festival is at Riverside Park, and tickets are ten dollars per day.",
                    "Children under twelve get in free, and there's a kids' baking class on Saturday at two p.m.",
                    "On Saturday, there's a cooking contest and a kids' baking class from eleven to nine. On Sunday, from eleven to six, there's a farmers' market and dessert tasting.",
                ),
                listOf(listOf("riverside", "ten"), listOf("free", "baking"), listOf("contest", "market", "dessert")),
            ),
            InfoSet(
                "Interviews: Marketing Assistant — Thursday, Room 3B",
                listOf(
                    "9:00 a.m." to "Kevin Lee (sales associate, Brightway Inc.)",
                    "10:00 a.m." to "Sarah Jones — CANCELED",
                    "11:00 a.m." to "Daniel Park (recent graduate, Hanover University)",
                    "2:00 p.m." to "Emma Wilson (marketing intern, Nova Media)",
                ),
                "Interviewers: Ms. Clark (HR), Mr. Diaz (Marketing Manager)",
                "Hi, this is Ms. Clark from HR. I don't have the interview schedule with me, so I'd like you to check some details.",
                listOf(
                    "Where are the interviews, and who is the first candidate?",
                    "I have another meeting at ten. Is that going to be a problem?",
                    "Could you give me the details of the interviews after ten o'clock?",
                ),
                listOf(
                    "The interviews are in Room 3B, and the first one is with Kevin Lee at nine a.m.",
                    "No, that's fine. The ten o'clock interview with Sarah Jones has been canceled, so you're free at that time.",
                    "Sure. At eleven a.m., you'll meet Daniel Park, a recent graduate from Hanover University. Then at two p.m., you'll interview Emma Wilson, who is currently a marketing intern at Nova Media.",
                ),
                listOf(listOf("3b", "kevin"), listOf("canceled"), listOf("daniel", "emma", "eleven", "two")),
            ),
        ),
        ToeicLevel.ADVANCED to listOf(
            InfoSet(
                "International Trade Conference — Grand Plaza Hotel, Oct. 21–22",
                listOf(
                    "Day 1, 9:00 a.m." to "Keynote: The Future of Global Shipping (Dr. Amy Chen)",
                    "Day 1, 11:00 a.m." to "Panel discussion: Trade Policy in Asia",
                    "Day 1, 2:00 p.m." to "Workshop: Negotiating Contracts (Ballroom B)",
                    "Day 2, 9:30 a.m." to "Digital Payments (Mark Evans) — MOVED to 1:00 p.m.",
                    "Day 2, 3:00 p.m." to "Closing remarks & networking reception",
                ),
                "Registration: \$250 (members \$180) · Lunch included both days",
                "Hello, I registered for the trade conference, but I can't find the schedule. Could you help me?",
                listOf(
                    "When and where is the conference being held?",
                    "I heard that Mark Evans is speaking on the morning of the second day. Is that correct?",
                    "I'm especially interested in the first day. Could you tell me what's happening on day one?",
                ),
                listOf(
                    "The conference is at the Grand Plaza Hotel on October twenty-first and twenty-second.",
                    "Not anymore. His presentation on digital payments was originally scheduled for nine thirty in the morning, but it has been moved to one p.m. on the second day.",
                    "Sure. At nine a.m., Dr. Amy Chen gives the keynote speech on the future of global shipping. At eleven, there's a panel discussion on trade policy in Asia. Then at two p.m., there's a workshop on negotiating contracts in Ballroom B.",
                ),
                listOf(listOf("grand", "twenty"), listOf("moved", "one"), listOf("chen", "panel", "workshop")),
            ),
            InfoSet(
                "Green Valley Apartments — Maintenance Notice, Week of March 3",
                listOf(
                    "Mon 9 a.m. – 12 p.m." to "Water shut off (Building A only)",
                    "Tue 1 – 5 p.m." to "Elevator inspection (Buildings A & B) — please use stairs",
                    "Wed (all day)" to "Parking lot repainting — park on Oak Street",
                    "Fri 10 a.m." to "Fire alarm test (all buildings, about 15 min.)",
                ),
                "Questions: Building office, ext. 210 · Open Mon–Fri, 8 a.m. – 6 p.m.",
                "Hi, I live in Building B. I got a maintenance notice, but I accidentally threw it away.",
                listOf(
                    "Will the water be turned off in my building this week?",
                    "I'm planning to move some furniture on Tuesday afternoon. Will that be a problem?",
                    "What other work is scheduled for later in the week?",
                ),
                listOf(
                    "No, the water will only be shut off in Building A, on Monday from nine a.m. to noon.",
                    "It might be. From one to five p.m. on Tuesday, the elevators in Buildings A and B are being inspected, so you'd have to use the stairs. You may want to move your furniture on another day.",
                    "On Wednesday, the parking lot is being repainted all day, so residents need to park on Oak Street. And on Friday at ten a.m., there will be a fire alarm test in all buildings, which should take about fifteen minutes.",
                ),
                listOf(listOf("building", "monday"), listOf("elevator", "stairs"), listOf("parking", "oak", "fire", "friday")),
            ),
            InfoSet(
                "Business Trip Itinerary — Rachel Moore, Tokyo",
                listOf(
                    "Mon, Nov. 4, 8:40 a.m." to "Depart Seoul (Flight KE703) · Arrive Tokyo 11:00 a.m.",
                    "Mon, 3:00 p.m." to "Meeting with Sato Electronics (Shinjuku office)",
                    "Tue, Nov. 5, 10:00 a.m." to "Factory tour (Yokohama)",
                    "Tue, 7:00 p.m." to "Dinner with Mr. Tanaka — CANCELED",
                    "Wed, Nov. 6, 2:15 p.m." to "Depart Tokyo · Arrive Seoul 4:40 p.m.",
                ),
                "Hotel: Park Tower Hotel (changed from 3 nights to 2 nights)",
                "Hi, this is Rachel Moore. I'm preparing for my business trip to Tokyo, and I'd like to confirm a few details.",
                listOf(
                    "What time does my flight leave on Monday, and when do I arrive?",
                    "I'm staying at the hotel for three nights, right?",
                    "Could you go over my schedule for Monday and Tuesday after I arrive?",
                ),
                listOf(
                    "Your flight leaves Seoul at eight forty a.m. on Monday, and you arrive in Tokyo at eleven a.m.",
                    "Actually, that was changed. You're staying at the Park Tower Hotel for two nights, not three.",
                    "Sure. On Monday at three p.m., you have a meeting with Sato Electronics at their Shinjuku office. On Tuesday at ten a.m., you'll take a factory tour in Yokohama. Your dinner with Mr. Tanaka on Tuesday evening has been canceled, so your evening is free.",
                ),
                listOf(listOf("eight", "eleven"), listOf("two", "changed"), listOf("sato", "factory", "canceled")),
            ),
            InfoSet(
                "Northwood Public Library — Author Talk Series (Thursdays, 7 p.m.)",
                listOf(
                    "Sept. 5" to "Lisa Grant — Cooking on a Budget (Room 101)",
                    "Sept. 12" to "Dr. Omar Haddad — The Science of Sleep (Main Hall)",
                    "Sept. 19" to "No event — library closed for staff training",
                    "Sept. 26" to "Lisa Grant — Healthy Meals for Busy Families (Room 101)",
                ),
                "Free for library members · Non-members \$5 · Books available for signing",
                "Hello, I'm calling about the author talk series at the library. I'd like some information.",
                listOf(
                    "What day and time are the talks held?",
                    "I'm not a library member. Can I still attend for free?",
                    "I'm a big fan of Lisa Grant. Could you tell me about her talks?",
                ),
                listOf(
                    "The talks are held on Thursdays at seven p.m.",
                    "You can still attend, but non-members have to pay a five-dollar fee. The talks are free only for library members.",
                    "Sure. Lisa Grant is giving two talks, both in Room 101. On September fifth, she'll talk about cooking on a budget, and on September twenty-sixth, her topic is healthy meals for busy families. Her books will also be available for signing.",
                ),
                listOf(listOf("thursday", "seven"), listOf("five", "members"), listOf("budget", "healthy", "101")),
            ),
        ),
    )

    // ---------------------------------------------------------------- Q11: express an opinion

    val opinions: Map<ToeicLevel, List<Opinion>> = mapOf(
        ToeicLevel.BASIC to listOf(
            Opinion(
                "Which do you prefer, eating at home or eating at a restaurant? Why?",
                "I prefer eating at home. First, it is cheaper. Eating at a restaurant every day costs a lot of money. Second, home food is healthier, because I can choose fresh food and use less salt and oil. For example, last weekend I made soup with my family, and it was delicious and healthy. So, I prefer eating at home.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? It is good to have a pet. Give reasons and examples to support your opinion.",
                "I agree that it is good to have a pet. First, pets make people happy. When I come home, my dog is always happy to see me, so I feel less tired. Second, pets help people exercise. I walk my dog every evening, so I am healthier now. For these reasons, I think having a pet is a good idea.",
            ),
            Opinion(
                "Which do you prefer for a vacation, the mountains or the beach? Why?",
                "I prefer the beach. First, I like swimming, and the beach is a great place to swim and relax in the summer. Second, there is usually delicious seafood near the beach. For example, last summer I went to Busan with my friends. We swam, ate fresh fish, and watched the sunset. It was a great trip. So, I like the beach more.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? Children should help with housework. Give reasons and examples to support your opinion.",
                "I agree that children should help with housework. First, they can learn to take care of themselves. For example, if they learn to wash dishes and clean their room, it will help them when they live alone later. Second, it helps the family. Parents are busy, so if children help, everyone has more free time together. So, I think children should help with housework.",
            ),
            Opinion(
                "Which is better for learning English, watching movies or reading books? Why?",
                "I think watching movies is better. First, I can hear how people really speak, so it helps my listening and pronunciation. Second, movies are fun, so I can study for a long time without getting bored. For example, I watch an English movie every weekend with subtitles, and now I can understand more words. That's why I think movies are better.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? It is better to live near your workplace. Give reasons to support your opinion.",
                "I agree. First, I can save time. If I live near my office, I can walk to work and sleep more in the morning. Second, I can save money on transportation. For example, my friend moved near her company last year, and now she has more time for exercise and hobbies. So, I think it is better to live near your workplace.",
            ),
            Opinion(
                "Which do you prefer, shopping online or shopping in a store? Why?",
                "I prefer shopping online. First, it is very easy and fast. I can buy things with my phone anytime, even late at night. Second, it is often cheaper, because there are many discounts and coupons. For example, I bought shoes online last month, and they were thirty percent cheaper than in the store. So, I like shopping online more.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? Exercising in the morning is better than exercising in the evening.",
                "I disagree. I think exercising in the evening is better. First, in the morning, I am busy getting ready for work, so I don't have enough time. Second, exercising in the evening helps me relax after a long day, and I sleep better at night. For example, I go jogging at eight p.m., and it helps me forget my stress. So, I prefer the evening.",
            ),
        ),
        ToeicLevel.MIDDLE to listOf(
            Opinion(
                "Do you agree or disagree with the following statement? It is better to work for a large company than a small company. Give specific reasons and examples to support your opinion.",
                "I think it is better to work for a large company. First, large companies usually offer higher salaries and better benefits, such as health insurance and long vacations. Second, there are more opportunities to grow. Large companies often have training programs, and employees can move to different departments. For example, my cousin works for a big electronics company, and last year he took a free leadership course and was promoted. For these reasons, I prefer working for a large company.",
            ),
            Opinion(
                "Some people think students should learn to cook at school. Others think it should be taught at home. Which do you think is better and why?",
                "I think students should learn to cook at school. First, not all parents have time to teach cooking, because many of them work long hours. If schools teach it, every student can learn this important life skill. Second, cooking classes can teach nutrition, so students can make healthier choices. For example, in middle school I took a cooking class and learned how to make simple, healthy meals, and I still use those recipes today. So, I believe school is the better place to learn cooking.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? Working from home is more productive than working in an office.",
                "I agree that working from home is more productive. First, people don't waste time commuting. For example, I used to spend almost two hours a day on the subway, but when I worked from home, I used that time to finish my tasks earlier. Second, there are fewer interruptions at home, like unnecessary meetings or chatting with coworkers. Of course, communication can be harder, but with video calls and messaging apps, this is not a big problem. So, I think working from home is more productive.",
            ),
            Opinion(
                "What is the most important quality for a team leader to have? Give reasons and examples to support your opinion.",
                "I think the most important quality for a team leader is good communication. First, a leader needs to explain goals clearly, so that everyone knows what to do. If the instructions are unclear, team members waste time and make mistakes. Second, a good communicator listens to team members and solves problems quickly. For example, my former manager held short meetings every Monday and always asked for our opinions. Because of that, our team worked well together and finished projects on time. That's why communication is the most important quality.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? People should spend more money on experiences, such as travel, than on things.",
                "I agree that people should spend more money on experiences. First, things lose their value over time, but memories from experiences last forever. For example, I don't remember most of the clothes I bought five years ago, but I still remember my trip to Japan with my family. Second, experiences like travel or concerts help us learn new things and build relationships with others. For these reasons, I think spending money on experiences is a better choice.",
            ),
            Opinion(
                "Some people prefer to live in a big city, while others prefer a small town. Which do you prefer, and why?",
                "I prefer to live in a big city. First, there are more job opportunities in a big city, so it's easier to build a career. Second, cities are more convenient. There are many hospitals, shopping malls, and good public transportation. For example, I live in Seoul, and I can get almost anywhere by subway in less than an hour. Although cities can be crowded and noisy, I think the advantages are much bigger. So, I prefer living in a big city.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? Companies should allow employees to wear casual clothes at work.",
                "I agree. First, employees feel more comfortable in casual clothes, so they can focus better on their work. Second, it saves money and time, because people don't need to buy expensive suits or iron shirts every morning. For example, my company changed its dress code last year, and many of my coworkers said they felt more relaxed and creative. Of course, people can still dress formally for important meetings with clients. So, I think casual clothes should be allowed.",
            ),
            Opinion(
                "What is the best way to make new friends after moving to a new city? Give reasons and examples to support your opinion.",
                "I think the best way is to join a club or a class based on your hobbies. First, you meet people who have the same interests, so it's easy to start a conversation. Second, you see the same people every week, so you naturally become closer. For example, when I moved to a new city for work, I joined a weekend hiking club. After a few months, I had several good friends, and we still travel together. So, joining a hobby group is the best way.",
            ),
        ),
        ToeicLevel.ADVANCED to listOf(
            Opinion(
                "Do you agree or disagree with the following statement? Governments should spend more money on public transportation than on building new roads. Give specific reasons and examples to support your opinion.",
                "I strongly agree with this statement. First of all, public transportation is a more sustainable solution to traffic problems. Building new roads often encourages more people to drive, so congestion returns within a few years. In contrast, an efficient subway or bus network can move thousands of people at once while producing far less pollution. Second, public transportation benefits everyone, including students, elderly people, and those who cannot afford a car. For example, when my city opened a new subway line, commuting times in my neighborhood dropped dramatically, and local businesses near the stations grew. For these reasons, I believe investing in public transportation is the wiser choice.",
            ),
            Opinion(
                "What are the advantages and disadvantages of using artificial intelligence in the workplace? Give reasons and examples to support your opinion.",
                "In my opinion, AI brings significant advantages to the workplace, though there are some drawbacks. The biggest advantage is efficiency. AI can handle repetitive tasks, like sorting e-mails or analyzing data, in seconds, which allows employees to focus on creative and strategic work. For instance, my team uses an AI tool to summarize customer feedback, and it saves us several hours every week. On the other hand, relying too much on AI can be risky. AI sometimes makes mistakes, and if employees don't check the results carefully, those errors can affect important decisions. Overall, I think AI is extremely useful, as long as people use it as an assistant rather than a replacement for human judgment.",
            ),
            Opinion(
                "Some people think companies should hire employees mainly based on their experience. Others believe that potential and attitude are more important. Which do you agree with, and why?",
                "I believe potential and attitude are more important than experience. To begin with, skills can be taught, but attitude is much harder to change. An employee who is motivated, curious, and willing to learn will quickly catch up and may eventually outperform someone with more experience. In addition, industries are changing so fast that past experience can become outdated. For example, a friend of mine was hired at a tech startup right after graduation, with no work experience. Because she was eager to learn, she became a team leader within two years. Therefore, I think companies should focus on potential and attitude when hiring.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? Social media has done more harm than good to society. Give specific reasons and examples to support your opinion.",
                "I partly agree with this statement. Although social media has clear benefits, I believe its negative effects have become more serious. First, social media spreads false information very quickly, which can cause confusion and even affect public health. Second, it can harm mental health, particularly among teenagers, who often compare themselves to the idealized lives they see online. Of course, social media helps people stay connected and allows small businesses to reach customers cheaply. However, unless platforms take stronger action against misinformation and excessive use, I think the harm outweighs the good.",
            ),
            Opinion(
                "Which is more important for a company's long-term success: satisfying its customers or keeping its employees happy? Give reasons and examples to support your opinion.",
                "Although customers are essential, I believe keeping employees happy is the key to long-term success. Satisfied employees are more productive and provide better service, which naturally leads to happier customers. In other words, employee satisfaction comes first. Moreover, when employees are unhappy, turnover increases, and companies have to spend a lot of time and money hiring and training new staff. For example, a coffee chain near my office offers generous benefits and training, and its staff rarely quit. Customers often mention the friendly service in their reviews. For these reasons, I think investing in employees is the best way to ensure long-term success.",
            ),
            Opinion(
                "Do you agree or disagree with the following statement? Universities should require all students to complete an internship before graduating.",
                "I agree with this statement. First, internships give students practical experience that they cannot get in a classroom. They learn how to communicate with coworkers, meet deadlines, and solve real problems, which makes them much better prepared for full-time jobs. Second, internships help students discover what they actually want to do. For example, I did an internship at a marketing firm during my third year, and I realized that I enjoyed data analysis much more than advertising. That experience completely changed my career path. One concern is that some students need part-time jobs to pay tuition, so universities should offer paid internship options. Overall, I believe required internships would benefit students greatly.",
            ),
            Opinion(
                "In the future, will people work fewer hours than they do today? Why or why not? Give reasons and examples to support your opinion.",
                "I believe people will work fewer hours in the future. The main reason is technology. As automation and AI take over routine tasks, the same amount of work can be done in less time. Another reason is that many companies are realizing that long hours don't necessarily lead to higher productivity. In fact, some companies that have tried a four-day work week have reported that employees became more focused and less likely to quit. However, this change may not happen equally in every industry; jobs in healthcare or services may still require long hours. Overall, though, I expect working hours to gradually decrease.",
            ),
            Opinion(
                "Some people say it is better for a company to make decisions quickly, even if they are sometimes wrong. Others think it is better to take time to make careful decisions. Which do you prefer, and why?",
                "I think it's generally better for companies to make careful decisions, although speed matters in some situations. Important decisions, such as entering a new market or making large investments, can affect a company for many years. If a company rushes, it might overlook risks and lose a lot of money. For example, a company I used to work for launched a product too quickly without enough testing, and it had to recall thousands of units, which damaged its reputation. Of course, in fast-changing industries, waiting too long can mean losing opportunities. So, the best approach is to decide carefully but set clear deadlines, so the process doesn't take forever.",
            ),
        ),
    )
}
