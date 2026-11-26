package app.claidered.reader

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private data class Skill(val name:String,val category:String,val path:String,val description:String)

class MainActivity:Activity(){
    private val io=Executors.newSingleThreadExecutor()
    private val skills=mutableListOf<Skill>()
    private var category:String?=null
    private lateinit var root:LinearLayout
    private lateinit var status:TextView
    private lateinit var search:EditText
    private lateinit var list:LinearLayout
    private val bg=Color.rgb(10,15,25)
    private val panel=Color.rgb(20,29,44)
    private val accent=Color.rgb(76,180,255)
    private val white=Color.rgb(235,242,250)
    private val muted=Color.rgb(154,171,190)

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);home();load()}
    private fun text(s:String,size:Float,color:Int=white)=TextView(this).apply{
        text=s;textSize=size;setTextColor(color);setPadding(0,7,0,7)
    }
    private fun btn(s:String,action:()->Unit)=Button(this).apply{text=s;setOnClickListener{action()}}
    private fun home(){
        root=LinearLayout(this).apply{orientation=1;setPadding(18,20,18,16);setBackgroundColor(bg)}
        root.addView(text("CLAUDE-RED",27f,accent))
        root.addView(text("Security skill library · Android reader",14f,muted))
        root.addView(text("Browse, read and share curated security skills with Claude.",15f))
        status=text("Connecting to GitHub…",13f,muted);root.addView(status)
        root.addView(btn("Load / refresh skills"){load()})
        search=EditText(this).apply{hint="Search skills or descriptions";setSingleLine(true);setTextColor(white);setHintTextColor(muted);setBackgroundColor(panel);setPadding(12,6,12,6)}
        root.addView(search)
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
            override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){render()}
            override fun afterTextChanged(e:android.text.Editable?){}
        })
        val sc=ScrollView(this);list=LinearLayout(this).apply{orientation=1};sc.addView(list)
        root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(text("Skill files are instructions, not executable tools. Test only with authorization.",12f,muted))
        setContentView(root);render()
    }
    private fun load(){
        if(!::status.isInitialized)return
        status.text="Loading upstream skill index…"
        io.execute{
            try{
                val json=JSONObject(get("https://raw.githubusercontent.com/SnailSploit/claude-red/main/claude-skills.json"))
                val arr=json.getJSONArray("skills");val found=mutableListOf<Skill>()
                for(i in 0 until arr.length()){
                    val x=arr.getJSONObject(i)
                    found.add(Skill(x.optString("name"),x.optString("category"),x.optString("path"),x.optString("description")))
                }
                runOnUiThread{skills.clear();skills.addAll(found);status.text=found.size.toString()+" skills loaded · upstream "+json.optString("version","latest");render()}
            }catch(e:Exception){runOnUiThread{status.text="Load failed: "+(e.message?:"network error")+". Check internet and retry."}}
        }
    }
    private fun render(){
        if(!::list.isInitialized)return
        list.removeAllViews()
        val cats=skills.map{it.category}.distinct().sorted()
        val row=LinearLayout(this).apply{orientation=0}
        row.addView(btn("All"){category=null;render()})
        cats.forEach{c->row.addView(btn(c.replace('-',' ')){category=if(category==c)null else c;render()})}
        list.addView(row)
        val q=if(::search.isInitialized)search.text.toString().trim().lowercase() else ""
        val found=skills.filter{(category==null||it.category==category)&&(q.isBlank()||it.name.contains(q)||it.description.lowercase().contains(q)||it.category.contains(q))}
        list.addView(text(found.size.toString()+" matching skills",13f,muted))
        found.forEach{s->
            val card=LinearLayout(this).apply{orientation=1;setPadding(12,8,12,8);setBackgroundColor(panel)}
            card.addView(text(s.name,17f,accent));card.addView(text(s.category.uppercase(),11f,muted));card.addView(text(s.description,13f))
            card.addView(btn("Read skill"){open(s)})
            val p=LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,8);list.addView(card,p)
        }
    }
    private fun open(s:Skill){
        root.removeAllViews();root.addView(btn("← Back to skills"){home()})
        root.addView(text(s.name,22f,accent));root.addView(text(s.category+" · "+s.path,12f,muted))
        root.addView(text("Loading skill document…",13f,muted))
        io.execute{try{val md=get("https://raw.githubusercontent.com/SnailSploit/claude-red/main/"+s.path);runOnUiThread{detail(s,md)}}
            catch(e:Exception){runOnUiThread{root.removeAllViews();root.addView(btn("← Back"){home()});root.addView(text("Unable to load skill: "+(e.message?:"network error"),14f))}}}
    }
    private fun detail(s:Skill,md:String){
        root.removeAllViews();root.addView(btn("← Back to skills"){home()})
        root.addView(text(s.name,22f,accent));root.addView(text(s.category+" · "+md.length+" characters",12f,muted))
        root.addView(btn("Share to Claude"){share(s.name,md)})
        root.addView(btn("Copy skill text"){
            val cb=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText(s.name,md))
            Toast.makeText(this,"Copied to clipboard",Toast.LENGTH_SHORT).show()
        })
        val sc=ScrollView(this);sc.addView(text(md,13f).apply{setTextIsSelectable(true)})
        root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(text("Review the content. Only conduct authorized security testing.",12f,muted))
    }
    private fun share(name:String,md:String){
        val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Claude-Red skill: "+name)
            putExtra(Intent.EXTRA_TEXT,"Use this skill as context for an authorized security assessment. Do not act on systems without explicit permission.\n\n"+md)}
        startActivity(Intent.createChooser(i,"Share skill with Claude"))
    }
    private fun get(address:String):String{
        val c=URL(address).openConnection() as HttpURLConnection
        c.connectTimeout=12000;c.readTimeout=20000;c.setRequestProperty("User-Agent","Claude-Red-Android/1.0")
        return try{if(c.responseCode !in 200..299)error("GitHub HTTP "+c.responseCode);c.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}}finally{c.disconnect()}
    }
    override fun onDestroy(){super.onDestroy();io.shutdownNow()}
}
