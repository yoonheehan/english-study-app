package com.grayman.englishstudy.ai

import com.grayman.englishstudy.data.Level
import kotlin.random.Random

/** One version of a topic: where it happens and what the learner should get done. */
data class Situation(val scene: String, val goalKo: String)

/** A role-play topic. The AI plays [aiRole]; the learner plays [learnerRole]. */
data class RolePlayTopic(
    val id: String,
    val titleKo: String,
    val descriptionKo: String,
    val group: String,
    val aiRole: String,
    val learnerRole: String,
    val situations: List<Situation>,
)

/**
 * One concrete run of a topic. A situation, a persona and a seed are drawn at random every time,
 * and the model is told to invent fresh details, so the same topic never plays out the same way.
 */
data class Scenario(
    val topic: RolePlayTopic,
    val situation: Situation,
    val partnerName: String,
    val mood: String,
    val seed: Int,
)

object RolePlay {
    private fun t(id: String, title: String, desc: String, group: String, ai: String, learner: String, vararg s: Pair<String, String>) =
        RolePlayTopic(id, title, desc, group, ai, learner, s.map { Situation(it.first, it.second) })

    val topics: List<RolePlayTopic> = listOf(
        // 일상
        t("cafe", "카페에서", "주문, 자리, 문제 해결까지", "일상", "a barista", "a customer",
            "The learner orders for a group of coworkers and is not sure what everyone wants." to "여러 명 음료를 주문하고, 메뉴를 물어보고, 합계를 확인해 보세요.",
            "The drink the learner wanted is sold out, and the barista suggests others." to "품절된 음료 대신 비슷한 것을 추천받아 골라 보세요.",
            "The learner received the wrong drink and wants it fixed politely." to "주문이 잘못 나왔다고 정중하게 말하고 바꿔 달라고 해 보세요."),
        t("smalltalk", "오랜만에 만난 친구", "근황, 약속 잡기", "일상", "an old friend", "the learner",
            "You bump into each other at a supermarket after a year." to "요즘 뭐 하는지 이야기하고 다음에 만날 날을 정해 보세요.",
            "Your friend just came back from living abroad for two years." to "해외 생활이 어땠는지 질문하고 내 근황도 말해 보세요.",
            "You meet at a mutual friend's wedding." to "결혼식 이야기를 하고, 서로의 최근 소식을 나눠 보세요."),
        t("neighbor", "이웃과 대화", "층간소음, 택배, 인사", "일상", "a neighbor in the same apartment building", "a resident",
            "The neighbor's music has been loud late at night." to "기분 나쁘지 않게 소음 문제를 말하고 해결책을 정해 보세요.",
            "A package for the learner was delivered to the neighbor by mistake." to "택배를 찾으러 가서 감사 인사를 하고 짧게 대화해 보세요.",
            "The neighbor has just moved in." to "자기소개를 하고 동네의 좋은 곳을 알려 주세요."),
        t("shopping", "쇼핑", "사이즈, 교환, 할인", "일상", "a shop assistant", "a shopper",
            "The learner is looking for a gift for a family member and has a budget." to "예산을 말하고 선물을 추천받아 골라 보세요.",
            "The learner wants to exchange shoes that are too small but lost the receipt." to "영수증이 없지만 교환이 되는지 협상해 보세요.",
            "There is a sale, but the learner is not sure the discount applies." to "할인이 적용되는지 확인하고 가격을 따져 보세요."),
        t("restaurant", "식당", "예약, 주문, 알레르기, 계산", "일상", "a server", "a diner",
            "The learner has a food allergy and needs to check the menu carefully." to "알레르기를 설명하고 먹을 수 있는 메뉴를 골라 보세요.",
            "The learner calls to book a table for a birthday dinner." to "날짜, 인원, 특별 요청을 말해 예약해 보세요.",
            "The bill looks wrong at the end of the meal." to "계산서가 이상한 부분을 정중하게 짚어 보세요."),
        t("phone", "전화 통화", "예약 변경, 메시지 남기기", "일상", "a receptionist on the phone", "a caller",
            "The learner needs to move a hair salon appointment to another day." to "예약을 다른 날로 바꾸고 시간을 확정해 보세요.",
            "The person the learner wants is out, so they leave a message." to "누구에게, 무슨 일로, 연락처까지 메시지를 남겨 보세요.",
            "The learner calls a gym to ask about membership." to "가격, 시간, 체험 가능 여부를 물어보세요."),
        // 친구·연애
        t("weekend", "주말 계획", "약속 잡고 의견 맞추기", "친구·연애", "a close friend", "the learner",
            "You are planning a weekend trip together but have different ideas." to "서로 원하는 곳을 말하고 절충안을 정해 보세요.",
            "Your friend wants to see a movie you do not like." to "다른 의견을 기분 좋게 말하고 대안을 제안해 보세요.",
            "You are organizing a small birthday party for another friend." to "장소, 음식, 선물을 나눠서 정해 보세요."),
        t("date", "첫 데이트", "취미와 관심사 대화", "친구·연애", "someone on a first date with the learner", "the learner",
            "You meet at a quiet cafe for a first date set up by friends." to "서로의 취미와 일에 대해 묻고 공통점을 찾아 보세요.",
            "The first date is a walk in a park on a sunny day." to "대화를 이어 가며 다음 데이트를 자연스럽게 제안해 보세요.",
            "The date is going well but the learner arrived 20 minutes late." to "늦은 이유를 설명하고 사과한 뒤 분위기를 살려 보세요."),
        t("advice", "고민 상담", "친구의 고민 들어 주기", "친구·연애", "a friend who has a problem", "a supportive friend",
            "Your friend is stressed about a job interview tomorrow." to "고민을 들어 주고 구체적인 조언을 해 보세요.",
            "Your friend is thinking about breaking up with their partner." to "판단하지 않고 들어 주며 질문으로 생각을 정리하게 도와주세요.",
            "Your friend wants to quit their job to start a business." to "장단점을 같이 따져 보며 의견을 말해 보세요."),
        t("apology", "사과와 화해", "실수 사과하기", "친구·연애", "a friend who is upset", "the learner",
            "The learner forgot the friend's birthday." to "진심으로 사과하고 만회할 방법을 제안해 보세요.",
            "The learner borrowed a book and damaged it." to "상황을 설명하고 어떻게 보상할지 말해 보세요.",
            "The learner said something careless at a party." to "무엇이 잘못이었는지 인정하고 관계를 회복해 보세요."),
        // 여행
        t("airport", "공항", "체크인, 연착, 수하물", "여행", "an airline agent", "a traveler",
            "The flight is delayed and the learner may miss a connection." to "연결편 문제를 설명하고 다른 항공편을 요청해 보세요.",
            "The learner's suitcase did not arrive." to "가방 생김새와 연락처를 설명하고 분실 신고를 해 보세요.",
            "The learner's bag is overweight at check-in." to "추가 요금을 피할 방법을 물어보고 해결해 보세요."),
        t("hotel", "호텔", "체크인, 방 문제, 추천", "여행", "a hotel front desk clerk", "a guest",
            "The room the learner booked is not ready yet." to "기다리는 동안 할 수 있는 것과 짐 보관을 요청해 보세요.",
            "The air conditioner in the room is broken." to "문제를 설명하고 방을 바꾸거나 고쳐 달라고 해 보세요.",
            "The learner wants recommendations for dinner nearby." to "음식 종류와 예산을 말하고 추천과 길을 물어보세요."),
        t("directions", "길 묻기", "교통, 시간, 길 확인", "여행", "a local person", "a tourist",
            "The learner is lost and needs to reach a museum before it closes." to "가는 방법과 시간을 묻고 들은 길을 다시 확인해 보세요.",
            "The learner needs to buy the right subway ticket." to "어떤 표를 사야 하는지, 어디서 갈아타는지 물어보세요.",
            "The learner wants a good local place that is not touristy." to "현지인 추천을 받아 보고 가는 방법도 물어보세요."),
        t("doctor", "병원·약국", "증상 설명, 처방", "여행", "a doctor at a clinic", "a patient",
            "The learner has had a fever and sore throat for two days while traveling." to "증상과 시작 시점을 설명하고 약 먹는 법을 물어보세요.",
            "The learner twisted an ankle while hiking." to "다친 상황을 설명하고 치료와 주의사항을 물어보세요.",
            "The learner needs a refill of regular medication abroad." to "평소 먹는 약을 설명하고 처방받는 방법을 물어보세요."),
        // 학교·대학
        t("professor", "교수님 면담", "성적, 과제 기한, 진로", "학교·대학", "a university professor", "a student",
            "The student needs an extension on a term paper." to "사정을 설명하고 기한 연장을 정중하게 요청해 보세요.",
            "The student thinks an exam was graded unfairly." to "근거를 들어 재검토를 요청해 보세요.",
            "The student wants advice about graduate school." to "관심 분야를 말하고 준비할 것을 물어보세요."),
        t("groupwork", "조별 과제", "역할 나누기, 갈등", "학교·대학", "a classmate on the same team", "a team member",
            "The team must split the work for a presentation due Friday." to "역할을 나누고 중간 점검 일정을 정해 보세요.",
            "One member has not done their part, and the deadline is close." to "비난하지 않고 상황을 묻고 해결책을 정해 보세요.",
            "The team disagrees on the topic of the project." to "내 의견을 근거와 함께 말하고 합의해 보세요."),
        t("dorm", "기숙사·룸메이트", "생활 규칙 정하기", "학교·대학", "a new roommate", "the learner",
            "You are setting house rules for cleaning and guests." to "청소 분담과 손님 규칙을 함께 정해 보세요.",
            "The roommate keeps using the learner's things without asking." to "기분 나쁘지 않게 불편함을 말해 보세요.",
            "You both have exams and need a quiet-hours plan." to "시험 기간 동안의 규칙을 정해 보세요."),
        t("club", "동아리 가입", "활동 묻기, 자기소개", "학교·대학", "a club leader at a club fair", "a student",
            "The learner is interested in a photography club." to "활동 내용과 비용을 묻고 자기소개를 해 보세요.",
            "The learner wants to join a volunteer club but has little time." to "가능한 시간을 말하고 참여 방법을 물어보세요.",
            "The learner wants to start a new club and asks for advice." to "새 동아리 아이디어를 설명하고 조언을 구해 보세요."),
        // 회사·업무
        t("interview", "영어 면접", "자기소개, 강점, 경험", "회사·업무", "a hiring manager", "a job candidate",
            "An interview for a marketing role at an international company." to "자기소개와 강점, 성과를 예를 들어 말해 보세요.",
            "An interview where the manager asks about a failure." to "실패 경험과 배운 점을 구체적으로 말해 보세요.",
            "The last part of an interview, where the candidate can ask questions." to "회사와 업무에 대해 좋은 질문을 2개 이상 해 보세요."),
        t("meeting", "회의", "의견, 반대, 제안", "회사·업무", "a manager leading a team meeting", "a team member",
            "The team discusses whether to delay a product launch." to "근거를 들어 의견을 말하고 대안을 제안해 보세요.",
            "The budget was cut and the team must decide what to drop." to "우선순위를 말하고 정중하게 반대 의견도 내 보세요.",
            "A brainstorming meeting about a new office event." to "아이디어를 내고 다른 의견에 덧붙여 보세요."),
        t("coworker", "동료와 업무 조율", "부탁, 거절, 일정", "회사·업무", "a coworker", "the learner",
            "A coworker asks the learner to take on extra work this week." to "상황을 설명하며 정중하게 거절하거나 조건을 걸어 보세요.",
            "The learner needs data from the coworker by tomorrow." to "필요한 것과 기한을 분명하게 부탁해 보세요.",
            "Two coworkers booked the same meeting room." to "양보하거나 시간을 조율해 해결해 보세요."),
        t("boss", "상사와 면담", "평가, 연봉, 휴가", "회사·업무", "the learner's manager", "an employee",
            "The yearly performance review." to "올해 성과를 말하고 내년 목표를 제안해 보세요.",
            "The employee wants to ask for a raise." to "근거를 들어 연봉 인상을 요청해 보세요.",
            "The employee needs two weeks off during a busy period." to "휴가가 필요한 이유와 업무 인수인계 계획을 말해 보세요."),
        t("presentation", "발표와 질의응답", "발표 후 질문 받기", "회사·업무", "an audience member asking tough questions", "a presenter",
            "The learner just presented quarterly sales results." to "숫자의 이유를 설명하고 질문에 침착하게 답해 보세요.",
            "The learner proposed a new app feature." to "기능의 장점과 비용 질문에 답해 보세요.",
            "The learner presented a plan to cut costs." to "반대 의견에 공감하면서 근거로 답해 보세요."),
        // 영업·비즈니스
        t("sales", "영업 미팅", "제품 소개, 가격 협상", "영업·비즈니스", "a potential client", "a salesperson",
            "Selling project-management software to a small company." to "고객의 문제를 묻고 제품이 어떻게 도움이 되는지 설명해 보세요.",
            "The client says the price is too high." to "가치를 강조하며 할인이나 조건을 협상해 보세요.",
            "The client is comparing you with a competitor." to "경쟁사와의 차이를 설득력 있게 말해 보세요."),
        t("negotiation", "계약 협상", "납기, 가격, 조건", "영업·비즈니스", "a supplier's representative", "a buyer",
            "Negotiating delivery dates for a large order." to "원하는 납기를 말하고 서로 맞는 조건을 찾아 보세요.",
            "The supplier wants to raise prices next year." to "인상 폭을 줄이도록 협상해 보세요.",
            "A shipment arrived late and some items were damaged." to "문제를 정리하고 보상을 요구해 보세요."),
        t("networking", "네트워킹 행사", "자기소개, 명함 교환", "영업·비즈니스", "a professional at an industry conference", "the learner",
            "Coffee break at a technology conference." to "자기소개를 하고 상대 일에 관심을 보이며 연락처를 교환해 보세요.",
            "A startup pitch event where you meet an investor." to "내 아이디어를 30초 안에 소개해 보세요.",
            "A dinner with potential business partners." to "가벼운 대화로 시작해 협업 가능성을 이야기해 보세요."),
        t("customer", "고객 응대", "불만 고객 응대", "영업·비즈니스", "an unhappy customer", "a customer service staff member",
            "A customer's online order arrived broken." to "사과하고 환불이나 교환 방법을 안내해 보세요.",
            "A customer was charged twice." to "상황을 확인하고 해결 일정을 안내해 보세요.",
            "A customer wants to cancel a subscription." to "이유를 묻고 혜택을 제안하되 원하면 해지를 도와주세요."),
        // 서비스·민원
        t("bank", "은행", "계좌, 카드 분실, 송금", "서비스·민원", "a bank clerk", "a customer",
            "The learner lost a debit card." to "카드 분실 신고와 재발급을 요청해 보세요.",
            "The learner wants to open an account as a foreigner." to "필요한 서류와 절차를 물어보세요.",
            "An international transfer has not arrived." to "송금 내역을 설명하고 확인을 요청해 보세요."),
        t("realestate", "집 구하기", "월세, 조건, 계약", "서비스·민원", "a real estate agent", "someone looking for an apartment",
            "The learner is looking for a one-bedroom near the subway." to "예산, 위치, 필요한 조건을 말하고 집을 보러 갈 약속을 잡아 보세요.",
            "The rent is a little over budget." to "월세를 깎거나 조건을 바꿔 보세요.",
            "The learner is checking an apartment in person." to "고장 난 곳, 관리비, 계약 기간을 꼼꼼히 물어보세요."),
        t("repair", "수리 요청", "고장 설명, 일정 잡기", "서비스·민원", "a repair technician on the phone", "a customer",
            "The washing machine leaks water." to "고장 증상을 설명하고 방문 일정을 잡아 보세요.",
            "The internet keeps disconnecting at home." to "언제부터 어떤 문제가 있는지 설명해 보세요.",
            "The laptop screen cracked under warranty." to "보증 수리 가능 여부와 비용을 물어보세요."),
        t("police", "경찰서·분실물", "분실 신고, 상황 설명", "서비스·민원", "a police officer", "a visitor",
            "The learner lost a wallet on a bus." to "언제 어디서 잃어버렸는지와 지갑 생김새를 설명해 보세요.",
            "The learner saw a minor car accident." to "목격한 내용을 순서대로 설명해 보세요.",
            "The learner's bicycle was stolen." to "자전거 특징과 상황을 설명하고 다음 절차를 물어보세요."),
        // 건강·생활
        t("gym", "헬스장·PT", "목표, 운동 계획", "건강·생활", "a personal trainer", "a gym member",
            "First session with a new personal trainer." to "운동 목표와 생활 습관을 말하고 계획을 함께 세워 보세요.",
            "The learner has back pain and needs safe exercises." to "통증을 설명하고 피해야 할 운동을 물어보세요.",
            "The learner wants to change their diet too." to "식단 고민을 말하고 조언을 받아 보세요."),
        t("cooking", "요리 수업", "재료, 순서 묻기", "건강·생활", "a cooking class instructor", "a student",
            "Learning to make pasta from scratch." to "순서와 팁을 질문하며 따라가 보세요.",
            "The learner cannot eat dairy and asks for substitutes." to "대체 재료를 물어보세요.",
            "The learner's dish did not turn out well." to "무엇이 잘못됐는지 묻고 고치는 법을 배워 보세요."),
        t("pet", "반려동물", "동물병원, 펫시터", "건강·생활", "a veterinarian", "a pet owner",
            "The dog has not been eating for a day." to "증상을 설명하고 검사와 비용을 물어보세요.",
            "The learner is going on a trip and needs a pet sitter." to "돌봄 방법과 주의사항을 설명해 보세요.",
            "The learner is thinking about adopting a cat." to "입양 절차와 준비물을 물어보세요."),
        // 시사·토론
        t("politics", "정치·선거 토론", "정책에 대한 의견", "시사·토론", "a friend who enjoys debating politics respectfully", "the learner",
            "Discussing whether voting should be required by law." to "내 의견과 이유 두 가지를 말하고 반론에 답해 보세요.",
            "Discussing whether the voting age should be lowered to 16." to "찬반 근거를 들어 토론해 보세요.",
            "Discussing what makes a good political leader." to "구체적인 자질을 예를 들어 말해 보세요."),
        t("environment", "환경 문제", "기후, 재활용, 정책", "시사·토론", "a debate partner", "the learner",
            "Should single-use plastic be banned completely?" to "찬반 의견과 현실적인 해결책을 말해 보세요.",
            "Is it the government's or individuals' job to fight climate change?" to "책임에 대한 내 생각을 근거와 함께 말해 보세요.",
            "Should cities ban cars from downtown areas?" to "장단점을 따져 의견을 말해 보세요."),
        t("technology", "기술과 AI", "AI, SNS, 일자리", "시사·토론", "a curious conversation partner", "the learner",
            "Will AI take more jobs than it creates?" to "내 의견과 예시를 들어 말해 보세요.",
            "Should children under 13 be allowed on social media?" to "찬반 근거를 말하고 대안을 제시해 보세요.",
            "Is remote work better than office work?" to "경험이나 예를 들어 의견을 말해 보세요."),
        t("economy", "경제와 생활비", "물가, 집값, 소비", "시사·토론", "a debate partner", "the learner",
            "Housing prices are rising fast in big cities." to "원인과 해결책에 대한 의견을 말해 보세요.",
            "Should there be a four-day work week?" to "생산성과 삶의 질 측면에서 의견을 말해 보세요.",
            "Is it better to save money or to spend on experiences?" to "내 가치관을 예를 들어 설명해 보세요."),
        t("society", "사회 이슈", "교육, 고령화, 문화", "시사·토론", "a debate partner", "the learner",
            "Should university education be free?" to "찬반 의견을 근거와 함께 말해 보세요.",
            "How should society prepare for an aging population?" to "구체적인 정책 아이디어를 말해 보세요.",
            "Should schools stop giving homework?" to "학생과 부모 입장에서 생각해 의견을 말해 보세요."),
        // 취미·문화
        t("movies", "영화·드라마 이야기", "추천, 감상 나누기", "취미·문화", "a friend who loves movies", "the learner",
            "Talking about a series you both just finished." to "좋았던 점과 아쉬운 점을 이야기해 보세요.",
            "Your friend asks you to recommend a Korean drama." to "줄거리와 추천 이유를 설명해 보세요.",
            "Deciding what to watch tonight." to "취향을 말하고 함께 고를 작품을 정해 보세요."),
        t("travelstory", "여행 이야기", "다녀온 여행 말하기", "취미·문화", "a curious friend", "the learner",
            "The friend asks about the learner's most memorable trip." to "어디서 무엇을 했고 왜 기억에 남는지 말해 보세요.",
            "The friend is planning a trip to Korea." to "꼭 가 볼 곳과 팁을 추천해 보세요.",
            "Talking about a trip that went wrong." to "무슨 일이 있었고 어떻게 해결했는지 이야기해 보세요."),
        t("hobby", "취미 이야기", "새 취미, 추천", "취미·문화", "a new acquaintance", "the learner",
            "You discover you both like hiking." to "좋아하는 코스와 경험을 이야기해 보세요.",
            "The learner wants to start a new hobby and asks for ideas." to "내 성향을 말하고 추천을 받아 보세요.",
            "Talking about games, music, or sports you enjoy." to "왜 좋아하는지 구체적으로 설명해 보세요."),
        t("koreanculture", "한국 문화 소개", "외국인에게 설명하기", "취미·문화", "a foreign coworker new to Korea", "the learner",
            "The coworker asks about Korean holidays like Chuseok." to "명절에 하는 일과 의미를 설명해 보세요.",
            "The coworker is confused about Korean dining etiquette." to "식사 예절을 예를 들어 설명해 보세요.",
            "The coworker wants to understand Korean work culture." to "회식, 직급 문화 등을 설명해 보세요."),
    )

    fun topic(id: String) = topics.first { it.id == id }

    /** A topic the learner describes in their own words (Korean is fine). */
    fun customTopic(description: String) = RolePlayTopic(
        id = "custom",
        titleKo = "내가 만든 상황",
        descriptionKo = description,
        group = "직접",
        aiRole = "the other person in this situation",
        learnerRole = "the learner",
        situations = listOf(Situation("The learner described this situation (it may be in Korean): \"$description\". Play the most natural other person in it.", description)),
    )

    private val names = listOf("Alex", "Jordan", "Sam", "Taylor", "Chris", "Morgan", "Jamie", "Riley", "Pat", "Casey", "Drew", "Robin")
    private val moods = listOf(
        "cheerful and talkative", "busy and a little impatient", "calm and patient", "friendly but curious, asking many questions",
        "slightly tired", "very polite and formal", "relaxed and joking", "skeptical, needing to be convinced",
    )

    fun scenario(topic: RolePlayTopic, random: Random = Random(System.nanoTime())) = Scenario(
        topic = topic,
        situation = topic.situations.random(random),
        partnerName = names.random(random),
        mood = moods.random(random),
        seed = random.nextInt(1000, 9999),
    )

    private fun levelStyle(level: Level): String = when (level.code) {
        "a1" -> "Use very short, simple sentences (5-8 words) and only the most common words. Speak slowly and clearly."
        "a2" -> "Use short, simple sentences and everyday words."
        "b1" -> "Use natural everyday English at an intermediate level."
        "b2" -> "Use natural, fluent English, including common idioms."
        else -> "Speak like a native speaker, with natural idioms and a normal pace."
    }

    /** Stay in character, keep turns short, invent fresh details, and never lecture during the scene. */
    fun systemInstruction(s: Scenario, level: Level): String = """
        You are role-playing to help a Korean learner practice spoken English.
        You are ${s.topic.aiRole}, named ${s.partnerName}. Your mood: ${s.mood}. The learner is ${s.topic.learnerRole}.
        Situation: ${s.situation.scene}
        Make this conversation unique (variation #${s.seed}): invent specific, realistic details such as names, prices,
        times, places and small surprises, and bring in at least one unexpected but realistic complication along the way.
        ${levelStyle(level)}
        Rules:
        - Stay in character and keep the conversation going. Reply in English only.
        - Keep every reply to one or two sentences, then ask or prompt something so the learner has to answer.
        - Do not correct the learner's mistakes during the role-play. If you cannot understand them, ask them to say it again, as a real person would.
        - Never write stage directions, emojis, or notes. Write only the words you say out loud.
    """.trimIndent()

    /** The first line comes from the model too, so each session starts differently. */
    const val START = "(Start the role-play now with your first line. Open in your own way, not with a generic greeting.)"

    fun hintPrompt(s: Scenario, level: Level, transcript: List<Pair<Boolean, String>>): String = """
        A Korean learner is practicing English in a role-play. They are ${s.topic.learnerRole}; the other person is ${s.topic.aiRole}.
        Situation: ${s.situation.scene}
        Conversation so far:
        ${format(transcript)}
        The learner is stuck. Suggest two different things the learner could say next, at this level: ${levelStyle(level)}
        Answer in this exact format and nothing else:
        1. <English sentence> — <한국어 뜻>
        2. <English sentence> — <한국어 뜻>
    """.trimIndent()

    /** Feedback after the scene: what went well, sentence fixes, and better phrasing, in Korean. */
    fun feedbackPrompt(s: Scenario, level: Level, transcript: List<Pair<Boolean, String>>): String = """
        You are an English speaking coach for a Korean learner (level ${level.code.uppercase()}).
        They just finished a role-play: they were ${s.topic.learnerRole}, talking to ${s.topic.aiRole}.
        Situation: ${s.situation.scene}
        Their goal: ${s.situation.goalKo}
        The learner spoke, and speech recognition wrote it down, so ignore spelling and punctuation; judge grammar, word choice and naturalness.
        Conversation:
        ${format(transcript)}

        Write the feedback in Korean, using exactly these sections:
        ## 잘한 점
        One or two short points.
        ## 고쳐 볼 문장
        Up to three of the learner's sentences that had mistakes or sounded unnatural. For each: "내 문장: ..." then "이렇게: ..." then one short reason in Korean.
        ## 써 볼 만한 표현
        Two or three natural English expressions useful in this situation, each with its Korean meaning.
        ## 목표 달성
        One sentence on whether they did the goal, and what to try next time.
    """.trimIndent()

    private fun format(transcript: List<Pair<Boolean, String>>) =
        transcript.joinToString("\n") { (learner, text) -> (if (learner) "Learner: " else "Partner: ") + text }
}


/** Prompts for "AI 해설" on wrong answers. Korean explanations, kept short. */
object Explain {
    fun listening(script: String, question: String, options: List<String>, answer: Int, chosen: Int?): String = """
        You are an English listening tutor for a Korean learner. Explain in Korean, briefly, why the correct answer is right
        and why the learner's choice is wrong. Quote the exact words in the audio that give the answer.
        Audio script:
        $script
        Question: $question
        Options:
        ${options.mapIndexed { i, o -> "${'A' + i}. $o" }.joinToString("\n")}
        Correct answer: ${'A' + answer}
        Learner chose: ${chosen?.let { "${'A' + it}" } ?: "nothing"}
        Use at most five short sentences. Then add one line starting with "기억할 표현:" with a useful phrase from the audio and its meaning.
    """.trimIndent()

    fun word(word: String, meaning: String, askedEnglish: Boolean, mine: String, example: String): String = """
        You are an English vocabulary tutor for a Korean learner. Answer in Korean, in at most four short sentences.
        Word: $word
        Correct meaning: $meaning
        ${if (askedEnglish) "The learner saw the Korean meaning and wrote this English word: \"$mine\"" else "The learner saw the English word and wrote this Korean meaning: \"$mine\""}
        ${if (example.isNotBlank()) "Example sentence: $example" else ""}
        Explain how the learner's answer differs from the correct one (if it is a different word, say what it means),
        give one tip to remember "$word", and one new short example sentence with its Korean meaning.
    """.trimIndent()
}
