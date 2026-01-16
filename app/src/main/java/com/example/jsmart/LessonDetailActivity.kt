package com.example.jsmart


import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.amrdeveloper.codeview.CodeView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.itextpdf.text.error_messages.MessageLocalization.setLanguage
import com.itextpdf.text.pdf.fonts.otf.Language
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.InputStream
import java.util.regex.Pattern

class LessonDetailActivity : AppCompatActivity() {
    private lateinit var codeView: CodeView
    private lateinit var outputConsole: TextView
    private var firebaseUserId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth

    private val JDoodleBaseURL = "https://api.jdoodle.com/v1/"
    private val clientId = "151ef874497d9fabe6a93317b494edbd"
    private val clientSecret = "38cabed9591eaed604362aa0ce0712100bead6847010e2a43a897a0a14fccc97"



    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson_detail)

        auth = FirebaseAuth.getInstance()


        firebaseUserId = auth.currentUser?.uid

        val tvLessonTitle = findViewById<TextView>(R.id.tvLessonTitle)
        val tvLessonContent = findViewById<TextView>(R.id.tvLessonContent)
        val lessonExampleContainer = findViewById<LinearLayout>(R.id.lessonExampleContainer)
        val tvLessonContent2 = findViewById<TextView>(R.id.tvLessonContent2)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnProceed = findViewById<Button>(R.id.btnProceed)
        val svLessonDetail = findViewById<ScrollView>(R.id.svLessonDetail)
        val quizContainer = findViewById<FrameLayout>(R.id.quizFragmentContainer)
        val btnRun = findViewById<MaterialButton>(R.id.btnRun1)

        val syntaxPatterns = mutableMapOf<Pattern, Int>()

        codeView = findViewById(R.id.codeView1)
        outputConsole = findViewById(R.id.outputConsole)

        val lessonId = intent.getIntExtra("lesson_id", 0)
        val jsonArray = loadJSONFromAssets()
        val lesson = jsonArray?.let { getLessonById(it, lessonId) }

        if (lesson != null) {
            tvLessonTitle.text = lesson.title
            tvLessonContent.text = lesson.content
            tvLessonContent2.text = lesson.content2
            if (lesson.content2.isNotEmpty()) {
                tvLessonContent2.text = lesson.content2
                tvLessonContent2.visibility = View.VISIBLE
            } else {
                tvLessonContent2.visibility = View.GONE
            }
            if (lesson.example.isNotEmpty()) {
                val exampleTextView = addExampleCard(lessonExampleContainer, lesson.example)
                boldWord(tvLessonContent, exampleTextView, tvLessonContent2, lessonId)
            } else {
                boldWord(tvLessonContent, null, tvLessonContent2, lessonId)
            }
        } else {
            tvLessonTitle.text = "Lesson Not Found"
            tvLessonContent.text = "No content available for this lesson."
        }

        syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)
        syntaxPatterns[Pattern.compile("\".*?\"|'.*?'|`.*?`")] = Color.rgb(180, 210, 255) // soft sky blue for strings
        syntaxPatterns[Pattern.compile("\\b\\d+\\.?\\d*\\b")] = Color.rgb(255, 190, 120) // warm orange
        syntaxPatterns[Pattern.compile("\\b(console\\.log|console\\.error|console\\.warn|console\\.info|console\\.debug)\\b")] =
            Color.rgb(255, 180, 120) // soft orange for logs
        syntaxPatterns[Pattern.compile("\\b(var|let|const|function|if|else|return|for|while|switch|case|break|continue|default|try|catch|finally|throw|new|delete|typeof|instanceof|in|do|async|await|yield|class|extends|super|this|import|export|from|as|static|get|set|constructor|debugger|with)\\b")] =
            Color.rgb(255, 140, 160) // rose pink — bright but not neon
        syntaxPatterns[Pattern.compile("\\b(Number|String|Boolean|Object|Array|Symbol|Math|Date|RegExp|JSON|Promise|Set|Map|WeakSet|WeakMap|BigInt)\\b")] =
            Color.rgb(255, 220, 120) // soft gold
        syntaxPatterns[Pattern.compile("\\b(console|document|window|alert|prompt|confirm|parseInt|parseFloat|isNaN|isFinite|decodeURI|encodeURI|setTimeout|setInterval|clearTimeout|clearInterval|localStorage|sessionStorage|fetch|require)\\b")] =
            Color.rgb(130, 210, 150) // soft green
        syntaxPatterns[Pattern.compile("\\b(length|toUpperCase|toLowerCase|push|pop|slice|splice|map|filter|reduce|forEach|join|replace|charAt|charCodeAt|includes|indexOf|addEventListener|removeEventListener|querySelector|querySelectorAll|getElementById|getElementsByClassName|getElementsByTagName|innerHTML|innerText|style|value)\\b")] =
            Color.rgb(120, 180, 255) // soft blue
        syntaxPatterns[Pattern.compile("\\b(true|false|null|undefined|NaN|Infinity)\\b")] =
            Color.rgb(190, 150, 255) // soft violet
        syntaxPatterns[Pattern.compile("[+\\-*/%=!<>&|^~?:]+")] =
            Color.rgb(255, 150, 150) // soft red
        syntaxPatterns[Pattern.compile("</?\\w+.*?>")] =
            Color.rgb(255, 180, 160) // soft coral for tags
        syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)


        codeView.setTextColor(Color.WHITE)
        codeView.setEnableLineNumber(true)
        codeView.setLineNumberTextSize(22f)
        codeView.setLineNumberTextColor(Color.WHITE)
        codeView.setEnableAutoIndentation(true)
        codeView.setEnableRelativeLineNumber(false)
        codeView.setEnableHighlightCurrentLine(true)
        codeView.setHighlightCurrentLineColor(Color.GRAY)
        codeView.setSyntaxPatternsMap(syntaxPatterns)
        codeView.setHighlightWhileTextChanging(true)
        codeView.reHighlightSyntax()
        codeView.setTextSize(13F)
        codeView.setPadding(50, 10, 10, 10)


        btnRun.setOnClickListener {
            val userCode = codeView.text.toString()
            if (userCode.isNotBlank()) executeCode(userCode)
            else outputConsole.text = "Please enter some code before running."
        }

        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }




        btnProceed.setOnClickListener {
            val data = hashMapOf(
                "progress" to 1
            )


            firebaseUserId?.let { uid ->
                db.collection("users")
                    .document(uid)
                    .collection("lessons")
                    .document("lesson_${lessonId}")
                    .set(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Progress saved!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }


            try {
                val json = assets.open("quiz.json").bufferedReader().use { it.readText() }
                val quizList = Gson().fromJson(json, Array<QuizModule>::class.java)
                val selectedQuiz = quizList.firstOrNull { it.id == lessonId }

                if (selectedQuiz != null) {
                    val intent = Intent(this, QuizDetailActivity::class.java)
                    intent.putExtra("QUIZ_DATA", Gson().toJson(selectedQuiz))
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Quiz not found for lesson $lessonId", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load quiz data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

    }



    private fun boldWord(textView: TextView, exampleTextView: TextView?, textView2: TextView,lessonId: Int) {
        val jsonArray = loadJSONFromAssets()
        val lesson = jsonArray?.let { getLessonById(it, lessonId) }

        if (lesson != null) {
            val lessonContent = lesson.content
            val exampleContent = lesson.example
            val lessonContent2 = lesson.content2

            val keywords = when (lessonId) {
                1 -> listOf("JavaScript","Why study JavaScript?","one of the 3 languages","console.log()","window.alert()","document.write()","innerHTML or innerText","innerHTML / innerText","JavaScript Display Possibilities","Key Points:","console.log(\"Message logged in the console using console.log()\");\n","</script>\n","</body>\n","</html>","window.alert(\"This is an alert box using window.alert()!\")","document.write(\"This text is written using document.write()<br>\");","document.getElementById(\"demo\").innerHTML = \"Hello, World! (Displayed in HTML Element)\";","<!DOCTYPE html>\n","<html>\n","<body>\n","<h2 id=\"demo\">JavaScript Display Example</h2>\n","<script>")
                2 -> listOf("Variables", "let", "const", "var", "letter", "$", "Name", "name",
                    "let age = 25;", "const pi = 3.14;", "var name = \"Alice\";", "Variable Naming Rules", "Declaring Variables", "Example:")
                3 -> listOf("string", "Example:","Common Errors & Mistakes","Forgetting quotes around strings:","Correct way:","let text = \"Hello, World!\";","console.log(text.length);","console.log(text.toUpperCase());","console.log(text.indexOf(\"World\"));")
                4 -> listOf("a function is a reusable block of code","Key Points:","Declaring a Function","Example:","function greet(name) {"," return \"Hello, \" + name + \"! Welcome to JavaScript.\";\n}","console.log(greet(\"Taylor\"));")
                5 -> listOf("Conditions", "true", "false","Comparison Operators","Operator & Meaning","Common Errors & Mistakes", "Example:", "Key Points:", "Declaring Conditions","function compareNumbers(a, b) {\n" +
                        "  if (a < b) {\n" +
                        "    console.log(\"a is less than b\");\n" +
                        "  } else if (a == b) {\n" +
                        "    console.log(\"a equals b\");\n" +
                        "  } else {\n" +
                        "    console.log(\"a is greater than b\");\n" +
                        "  }\n" +
                        "}\n" +
                        "\n" +
                        "compareNumbers(5, 10);")
                6 -> listOf("array","Declaring an Array","Example:","Accessing Array Elements","Changing an Array Element","Array Length","Key Points:","let cars = [\"Saab\", \"Volvo\", \"BMW\"];","console.log(cars[0]);","cars[0] = \"Toyota\";\n" +
                        "console.log(cars);","console.log(cars.length);")
                7 -> listOf("Example:", "Declaring an Object","Key Points:","let car = {\n","  type: \"Sedan\",\n","  model: \"Honda Civic\",\n","  color: \"Black\",\n","  start: function() {\n","    console.log(\"The car is starting...\");\n" ,"  }\n","};\n", "\n", "console.log(car.model);","console.log(car.model);","car.start();")
                8 -> listOf("Loops","for loop", "while loop", "do…while loop","Types of Loops:","Key Points:","break","continue","Example:","for (let i = 1; i <= 5; i++) {","console.log(\"Number: \" + i);\n","}")
                9 -> listOf("class", "Example:","Creating a Class","Constructor","Getter (Computed Property)","Methods", "Key Points:","class Rectangle {\n" +
                        "  constructor(width, height) {","this.width = width;\n" + "    this.height = height;","get area() {","const rect = new Rectangle(5, 10);\n" + "console.log(rect.area);")
                10 -> listOf("Modules", "Example:","Exporting Code","Importing Code","Key Points:","export function add(a, b) {","return a + b;\n}","import { add } from './mathTools.js';","console.log(add(2, 3));")
                11 -> listOf("Global Scope","Local Scope","Block Scope (let and const)","this Keyword","Arrow Functions and this", "Example:","Key Points:","let globalVar = \"I’m global\";","function showScope() {","let localVar = \"I’m local\";"," if (true) {\n","let blockVar = \"I’m block-scoped\"; ","console.log(blockVar);\n" +
                        "  }\n" +
                        "  console.log(localVar);\n" +
                        "}\n" +
                        "\n" +
                        "showScope();\n" +
                        "console.log(globalVar);")
                12 -> listOf("Math object","Vec Class Example","Finding Distance","Finding Angles","Example:","Key Points:","const x1 = 0, y1 = 0;\n" +
                        "const x2 = 3, y2 = 4;\n" +
                        "\n" +
                        "const distance = Math.sqrt((x2 - x1)**2 + (y2 - y1)**2);\n" +
                        "console.log(distance);")
                13 -> listOf("JSON (JavaScript Object Notation)","Structure:","Loading JSON with fetch","Error Handling","Finding Data in JSON","Key Points:", "Example:","fetch('./students.json')\n" +
                        "  .then(response => response.json())"," .then(data => {\n" +
                        "    console.log(data.name);"," })\n" +
                        "  .catch(error => console.error(error));")
                else -> listOf()
            }

            val spannableContent = applyBold(lessonContent, keywords)
            textView.text = spannableContent

            if (lessonContent2.isNotEmpty()) {
                val spannableContent2 = applyBold(lessonContent2, keywords)
                textView2.text = spannableContent2
            }
        }
    }


    private fun applyBold(text: String, keywords: List<String>): SpannableString {
        val spannable = SpannableString(text)

        for (word in keywords) {
            var start = text.lowercase().indexOf(word.lowercase())

            while (start != -1) {
                val end = start + word.length
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                start = text.lowercase().indexOf(word.lowercase(), end)
            }
        }
        return spannable
    }

    private fun executeCode(code: String) {
        val request = JDoodleRequest(clientId, clientSecret, code, "nodejs", "4")

        ApiClient.apiService.executeCode(request).enqueue(object : Callback<JDoodleResponse> {
            override fun onResponse(call: Call<JDoodleResponse>, response: Response<JDoodleResponse>) {
                outputConsole.text = if (response.isSuccessful) {
                    response.body()?.output ?: "No output"
                } else {
                    "Execution failed: ${response.errorBody()?.string()}"
                }
            }

            override fun onFailure(call: Call<JDoodleResponse>, t: Throwable) {
                outputConsole.text = "Error: ${t.message}"
            }
        })
    }

    private fun loadJSONFromAssets(): JSONArray? {
        return try {
            val inputStream: InputStream = assets.open("lessons.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charsets.UTF_8)

            JSONObject(jsonString).getJSONArray("lessons")
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }


    private fun getLessonById(jsonArray: JSONArray, id: Int): Lesson? {
        for (i in 0 until jsonArray.length()) {
            val lesson = jsonArray.getJSONObject(i)
            if (lesson.getInt("id") == id) {
                val title = lesson.getString("title")
                val content = lesson.getString("content")
                val example = lesson.optString("example", "")
                val content2 = lesson.optString("content2")
                return Lesson(title, content, example, content2)
            }
        }
        return null
    }

    private fun addExampleCard(container: LinearLayout, exampleText: String): CodeView {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(10, 10, 10, 10)
            }
            radius = 12f
            cardElevation = 6f
        }

        val codeView = CodeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )



            setLineNumberTextColor(Color.DKGRAY)
            setTextSize(12F)

            setHorizontallyScrolling(true)

            isFocusable = false
            isClickable = false
            isEnabled = false
            setBackgroundColor(Color.rgb(45, 48, 53))
            setTextColor(Color.rgb(248, 248, 242))
            setPadding(25, 20, 25, 20)

            val syntaxPatterns = mutableMapOf<Pattern, Int>()

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("\".*?\"|'.*?'|`.*?`")] = Color.rgb(180, 210, 255) // soft sky blue for strings

            syntaxPatterns[Pattern.compile("\\b\\d+\\.?\\d*\\b")] = Color.rgb(255, 190, 120) // warm orange

            syntaxPatterns[Pattern.compile("\\b(console\\.log|console\\.error|console\\.warn|console\\.info|console\\.debug)\\b")] =
                Color.rgb(255, 180, 120) // soft orange for logs

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("\\b(var|let|const|function|if|else|return|for|while|switch|case|break|continue|default|try|catch|finally|throw|new|delete|typeof|instanceof|in|do|async|await|yield|class|extends|super|this|import|export|from|as|static|get|set|constructor|debugger|with)\\b")] =
                Color.rgb(255, 140, 160) // rose pink — bright but not neon

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("\\b(Number|String|Boolean|Object|Array|Symbol|Math|Date|RegExp|JSON|Promise|Set|Map|WeakSet|WeakMap|BigInt)\\b")] =
                Color.rgb(255, 220, 120) // soft gold

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("\\b(console|document|window|alert|prompt|confirm|parseInt|parseFloat|isNaN|isFinite|decodeURI|encodeURI|setTimeout|setInterval|clearTimeout|clearInterval|localStorage|sessionStorage|fetch|require)\\b")] =
                Color.rgb(130, 210, 150) // soft green

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("\\b(length|toUpperCase|toLowerCase|push|pop|slice|splice|map|filter|reduce|forEach|join|replace|charAt|charCodeAt|includes|indexOf|addEventListener|removeEventListener|querySelector|querySelectorAll|getElementById|getElementsByClassName|getElementsByTagName|innerHTML|innerText|style|value)\\b")] =
                Color.rgb(120, 180, 255) // soft blue

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("\\b(true|false|null|undefined|NaN|Infinity)\\b")] =
                Color.rgb(190, 150, 255) // soft violet

            syntaxPatterns[Pattern.compile("[+\\-*/%=!<>&|^~?:]+")] =
                Color.rgb(255, 150, 150) // soft red

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)

            syntaxPatterns[Pattern.compile("</?\\w+.*?>")] =
                Color.rgb(255, 180, 160) // soft coral for tags

            syntaxPatterns[Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/")] = Color.rgb(180, 180, 180)


            setSyntaxPatternsMap(syntaxPatterns)
            reHighlightSyntax()
            setText(exampleText)
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            setBackgroundColor(Color.rgb(45, 48, 53))
            setPadding(0, 0, 0, 0)


            clipToPadding = false
            clipToOutline = false
        }

        scroll.addView(codeView)
        cardView.addView(scroll)

        container.addView(cardView)

        return codeView
    }

    override fun onBackPressed() {
        val quizContainer = findViewById<FrameLayout>(R.id.quizFragmentContainer)
        if (quizContainer.visibility == View.VISIBLE) {
            quizContainer.visibility = View.GONE
            findViewById<ScrollView>(R.id.svLessonDetail).visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }

}
