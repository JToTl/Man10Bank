package red.man10.man10bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.atm.ATMInventory
import red.man10.man10bank.atm.ATMListener
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.history.EstateData
import red.man10.man10bank.loan.Event
import red.man10.man10bank.loan.LoanCommand
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10bank.loan.ServerLoanCommand
import red.man10.man10score.ScoreDatabase
import java.text.Normalizer
import java.text.SimpleDateFormat


class Man10Bank : JavaPlugin(),Listener {

    companion object{
        const val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var vault : VaultManager

        lateinit var plugin : Man10Bank

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }

        fun format(double: Double):String{
            return String.format("%,.0f",double)
        }

        const val OP = "man10bank.op"

        var bankEnable = true

        var loanFee : Double = 1.0
        var loanRate : Double = 1.0
        var loanMax : Double = 10000000.0

        var paymentThread = false
        var loggingServerHistory = false
    }

    private val checking = HashMap<Player,Command>()

    override fun onEnable() {
        // Plugin startup logic

        plugin = this

        saveDefaultConfig()

        mysqlQueue()

        vault = VaultManager(this)

        loadConfig()

        server.pluginManager.registerEvents(this,this)
        server.pluginManager.registerEvents(Event(),this)
        server.pluginManager.registerEvents(ATMListener,this)
        server.pluginManager.registerEvents(Cheque,this)

        getCommand("mlend")!!.setExecutor(LoanCommand())
        getCommand("mrevo")!!.setExecutor(ServerLoanCommand())
        getCommand("baltest")!!.setExecutor(TestCommand())

//        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { Bank.bankQueue() })

//        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { EstateData.historyThread() })

//        if (paymentThread){
//            Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {ServerLoan.paymentThread()})
//        }

    }

    override fun onDisable() {
        // Plugin shutdown logic
        mysqlQueue.add("quit")
        Bukkit.getScheduler().cancelTasks(this)
    }

    private fun loadConfig(){

        reloadConfig()

        loanFee = config.getDouble("loanfee",1.1)
        loanMax = config.getDouble("loanmax",10000000.0)
        loanRate = config.getDouble("loanrate",1.0)
        loggingServerHistory = config.getBoolean("loggingServerHistory",false)
        paymentThread = config.getBoolean("paymentThread",false)

        ServerLoan.medianMultiplier = config.getDouble("medianMultiplier")
        ServerLoan.recordMultiplier = config.getDouble("recordMultiplier")
        ServerLoan.scoreMultiplier = config.getDouble("scoreMultiplier")
        ServerLoan.maxServerLoanAmount = config.getDouble("maxServerLoan")
        ServerLoan.revolvingFee = config.getDouble("revolvingFee")
        ServerLoan.lastPaymentCycle = config.getInt("lastPaymentCycle")

        ATMData.loadItem()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {


        when(label){

            "mchequeop" ->{//mchequeop amount <memo>
                if (sender !is Player)return false
                if (!sender.hasPermission(OP))return false

                val amount = args[0].toDoubleOrNull()?:return false
                val note = if (args.size>1)args[1] else null

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { Cheque.createCheque(sender,amount,note,true) })

                return true
            }

            "atm" ->{
                if (sender !is Player)return false

                if (args.isEmpty()){
                    if (!bankEnable)return false
                    ATMInventory.openMainMenu(sender)
                    return true
                }

                when(args[0]){

                    "setmoney"->{

                        if (!sender.hasPermission(OP))return false

                        val amount = args[1].toDoubleOrNull() ?: return true

                        val ret = ATMData.setItem(sender.inventory.itemInMainHand,amount)

                        if (ret){
                            sendMsg(sender,"設定完了:${amount}")
                        }
                    }
                }

            }

            "mbaltop" ->{

                val page = if (args.isEmpty()) 1 else args[0].toIntOrNull()?:1

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val balTopMap = EstateData.getBalanceTop(page)
                    val total = EstateData.getBalanceTotal()

                    var i = (page*10)-9

                    if (sender is Player){

                        sendMsg(sender,"§6§k§lXX§e§l富豪トップ${page*10}§6§k§lXX")

                        for (data in balTopMap){
                            sendMsg(sender,"§7§l${i}.§b§l${data.first} : §e§l${format(data.second)}円")
                            i++
                        }

                        sendMsg(sender,"§e§l電子マネーの合計:${format(total.vault)}円")
                        sendMsg(sender,"§e§l現金の合計:${format(total.cash)}円")
                        sendMsg(sender,"§e§l銀行口座の合計:${format(total.bank)}円")
                        sendMsg(sender,"§e§lその他資産の合計:${format(total.estate)}円")
                        sendMsg(sender,"§c§l公的ローンの合計:${format(total.loan)}円")
                        sendMsg(sender,"§e§l全ての合計:${format(total.total())}円")

                        return@Runnable
                    }

                    sender.sendMessage("§6§k§lXX§e§l富豪トップ${page*10}§6§k§lXX")

                    for (data in balTopMap){
                        sender.sendMessage("§7§l${i}.§b§l${data.first} : §e§l${format(data.second)}円")
                        i++
                    }
                    sender.sendMessage("§e§l電子マネーの合計:${format(total.vault)}円")
                    sender.sendMessage("§e§l現金の合計:${format(total.estate)}円")
                    sender.sendMessage("§e§l銀行口座の合計:${format(total.bank)}円")
                    sender.sendMessage("§c§l公的ローンの合計:${format(total.loan)}円")
                    sender.sendMessage("§e§l全ての合計:${format(total.total())}円")

                })

            }

            "mloantop" ->{

                val page = if (args.isEmpty()) 1 else args[0].toIntOrNull()?:1

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val loanTop = ServerLoan.getLoanTop(page)
                    val total = EstateData.getBalanceTotal()

                    var i = (page*10)-9

                    if (sender is Player){

                        sendMsg(sender,"§4§k§lXX§c§l借金トップ${page*10}§4§k§lXX")

                        for (data in loanTop){
                            sendMsg(sender,"§c§l${i}.§l${data.first} : §4§l${format(data.second)}円")
                            i++
                        }

                        sendMsg(sender,"§c§l公的ローンの合計:${format(total.loan)}円")

                        return@Runnable
                    }

                    sender.sendMessage("§4§k§lXX§c§l借金トップ${page*10}§4§k§lXX")

                    for (data in loanTop){
                        sender.sendMessage("§c§l${i}.§l${data.first} : §4§l${format(data.second)}円")
                        i++
                    }

                    sender.sendMessage("§c§l公的ローンの合計:${format(total.loan)}円")

                })

            }

            "bal","balance","money","bank" ->{

                if (sender !is Player)return false

                if (args.isEmpty()){
                    Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { showBalance(sender,sender) })
                    return true
                }

                when(args[0]){

                    "help" ->{
                        showCommand(sender)
                    }

                    "log" ->{

                        val page = if (args.size>=2) args[1].toIntOrNull()?:0 else 0

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val list = Bank.getBankLog(sender,page)

                            sendMsg(sender,"§d§l===========銀行の履歴==========")
                            for (data in list){

                                val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                                sendMsg(sender,"$tag §e${data.dateFormat} §e§l${format(data.amount)} §e${data.note}")

                            }

                            val previous = if (page!=0) {
                                text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/bal log ${page-1}"))
                            }else text(prefix)

                            val next = if (list.size == 10){
                                text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/bal log ${page+1}"))
                            }else text("")

                            sender.sendMessage(previous.append(next))

                        })

                        return true
                    }

                    "logop" ->{

                        if (!sender.hasPermission(OP))return false

                        val p = if (args.size >= 2)Bukkit.getPlayer(args[1]) else sender
                        val page = if (args.size>=2) args[2].toIntOrNull()?:0 else 0

                        if (p == null){
                            sender.sendMessage("プレイヤーがオフラインです")
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val list = Bank.getBankLog(p,page)

                            sendMsg(sender,"§d§l===========銀行の履歴==========")
                            for (data in list){

                                val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                                sendMsg(sender,"$tag §e${data.dateFormat} ${data.note} ${format(data.amount)}")

                            }

                            val previous = if (page!=0) {
                                text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/bal logop ${page-1}"))
                            }else text(prefix)

                            val next = if (list.size == 10){
                                text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/bal logop ${page+1}"))
                            }else text("")

                            sender.sendMessage(previous.append(next))

                        })

                    }

                    "init" ->{
                        if (!sender.hasPermission(OP))return false

                        if (args.size!=2)return false

                        if (checking[sender]== null || checking[sender]!=command){
                            sendMsg(sender,"データは復元できません。確認のため、もう一度入力してください")
                            checking[sender] = command
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            Bank.init(args[1])
                            sendMsg(sender,"${args[1]}のデータを初期化しました")

                        })

                        checking.remove(sender)

                    }

                    "take" ->{
                        if (!sender.hasPermission(OP))return true

                        val a = args[2].replace(",","")

                        if (!NumberUtils.isDigits(a)){
                            sendMsg(sender,"§c§l回収する額を半角数字で入力してください！")
                            return true
                        }

                        val amount = a.toDouble()

                        if (amount < 0){
                            sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val uuid = Bank.getUUID(args[1])?: return@Runnable

                            if (Bank.withdraw(uuid,amount,this,"TakenByCommand","サーバーから徴収").first!=0){
                                Bank.setBalance(uuid,0.0)
                                sendMsg(sender,"§a回収額が残高を上回っていたので、残高が0になりました")
                                return@Runnable
                            }
                            sendMsg(sender,"§a${format(amount)}円回収しました")
                            sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                        })

                        return true

                    }

                    "give"->{
                        if (!sender.hasPermission(OP))return true

                        val a = args[2].replace(",","")

                        if (!NumberUtils.isDigits(a)){
                            sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                            return true
                        }

                        val amount = a.toDouble()

                        if (amount < 0){
                            sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val uuid =  Bank.getUUID(args[1])?: return@Runnable

                            Bank.deposit(uuid,amount,this,"GivenFromServer","サーバーから発行")

                            sendMsg(sender,"§a${format(amount)}円入金しました")
                            sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                        })

                    }

                    "set"->{
                        if (!sender.hasPermission(OP))return true

                        val a = args[2].replace(",","")

                        if (!NumberUtils.isDigits(a)){
                            sendMsg(sender,"§c§l設定する額を半角数字で入力してください！")
                            return true
                        }

                        val amount = a.toDouble()

                        if (amount < 0){
                            sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val uuid =  Bank.getUUID(args[1])?: return@Runnable

                            Bank.setBalance(uuid,amount)

                            sendMsg(sender,"§a${format(amount)}円に設定しました")

                        })
                    }

                    "on" ->{
                        if (!sender.hasPermission(OP))return false

                        bankEnable = true

                        Bukkit.broadcast(text("§e§lMan10Bankが開きました！"))
                        return true

                    }

                    "off" ->{
                        if (!sender.hasPermission(OP))return false

                        bankEnable = false
                        Bukkit.broadcast(text("§e§lMan10Bankが閉じました！"))
                        return true

                    }

                    "reload" ->{
                        if (!sender.hasPermission(OP))return false

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            reloadConfig()
                            loadConfig()
                            Bank.reload()
                        })

                    }

                    else ->{
                        val p = Bukkit.getOfflinePlayer(args[0]).player

                        if (!sender.hasPermission(OP))return true

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            if (p==null){
                                EstateData.showOfflineUserEstate(sender,args[0])
                                return@Runnable
                            }

                            showBalance(sender,p)
                        })

                        return true

                    }
                }

            }

            "deposit" ->{

                if (sender !is Player)return false

                if (!bankEnable)return false

                if (args.isEmpty()){
                    sendMsg(sender,"§c§l/deposit <金額> : 銀行に電子マネーを入れる")
                    return true
                }

                //入金額
                val amount : Double = if (args[0] == "all"){
                    vault.getBalance(sender.uniqueId)
                }else{

                    val a = args[0].replace(",","")

                    val b = ZenkakuToHankaku(a)

                    if (b == -1.0){
                        sendMsg(sender,"§c§l数字で入力してください！")
                        return true
                    }
                    b
                }

                if (amount < 1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l電子マネーが足りません！")
                    return true

                }

                Bank.asyncDeposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand","/depositによる入金"){ code:Int,_:Double,_:String ->

                    if (code != 0){
                        sendMsg(sender,"入金エラーが発生しました")
                        vault.deposit(sender.uniqueId,amount)
                        return@asyncDeposit
                    }

                    if (code == 0){ sendMsg(sender,"§a§l入金できました！") }
                }

                return true


            }

            "withdraw" ->{

                if (sender !is Player)return false

                if (!bankEnable)return false

                if (args.isEmpty()){
                    sendMsg(sender,"§c§l/withdraw <金額> : 銀行から電子マネーを出す")
                    return true
                }


                val amount = if (args[0] == "all"){
                    Bank.getBalance(sender.uniqueId)
                }else{

                    val a = args[0].replace(",","")

                    val b = ZenkakuToHankaku(a)

                    if (b == -1.0){
                        sendMsg(sender,"§c§l数字で入力してください！")
                        return true
                    }
                    b
                }

                if (amount < 1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }


                Bank.asyncWithdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand","/withdrawによる出金") { code: Int, _: Double, _: String ->
                    if (code == 2){
                        sendMsg(sender,"§c§l銀行のお金が足りません！")
                        return@asyncWithdraw
                    }
                    vault.deposit(sender.uniqueId,amount)
                    sendMsg(sender,"§a§l出金できました！")
                }

                return true
            }

            "pay" ->{
//                if (!sender.hasPermission(USER))return true

                if (sender !is Player)return true

                if (!isEnabled)return true

                if (args.size != 2){
                    sendMsg(sender,"§c§l/pay <送る相手> <金額> : 電子マネーを友達に振り込む")
                    return true
                }

                val a = args[1].replace(",","")

                val amount = ZenkakuToHankaku(a)

                if (amount == -1.0){
                    sendMsg(sender,"§c§l/pay <送る相手> <金額> : 電子マネーを友達に振り込む")
                    return true
                }

                if (amount <1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                if (checking[sender] == null||checking[sender]!! != command){

                    sendMsg(sender,"§7§l送る電子マネー:${format(amount)}円")
                    sendMsg(sender,"§7§l送る相手:${args[0]}")
                    sendMsg(sender,"§7§l確認のため、同じコマンドをもう一度入力してください")

                    checking[sender] = command

                    return true
                }

                checking.remove(sender)

                val p = Bukkit.getPlayer(args[0])

                if (p==null){
                    sendMsg(sender,"§c§l送る相手がオフラインかもしれません")
                    return true
                }

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l送る電子マネーが足りません！")
                    return true
                }

                vault.deposit(p.uniqueId,amount)

                sendMsg(sender,"§a§l送金できました！")
                sendMsg(p,"§a${sender.name}さんから${format(amount)}円送られました！")

                return true

            }

            "mpay" ->{
//                if (!sender.hasPermission(USER))return true

                if (sender !is Player)return true

                if (!isEnabled)return true

                if (args.size != 2){
                    sendMsg(sender,"§c§l/mpay <送る相手> <金額> : 銀行のお金を友達に振り込む")
                    return false
                }

                val a = args[1].replace(",","")

                val amount = ZenkakuToHankaku(a)

                if (amount == -1.0){
                    sendMsg(sender,"§c§l/mpay <送る相手> <金額> : 銀行のお金を友達に振り込む")
                    return true
                }

                if (amount <1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                if (checking[sender] == null||checking[sender]!! != command){

                    sendMsg(sender,"§7§l送る銀行のお金:${format(amount)}円")
                    sendMsg(sender,"§7§l送る相手:${args[0]}")
                    sendMsg(sender,"§7§l確認のため、同じコマンドをもう一度入力してください")

                    checking[sender] = command

                    return true
                }

                checking.remove(sender)

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable  {
                    val uuid = Bank.getUUID(args[0])

                    if (uuid == null){
                        sendMsg(sender,"§c§l送金失敗！まだman10サーバーにきたことがない人かもしれません！")
                        return@Runnable
                    }


                    if (Bank.withdraw(sender.uniqueId,amount,this,"RemittanceTo${args[0]}","${args[0]}へ送金").first != 0){
                        sendMsg(sender,"§c§l送金する銀行のお金が足りません！")
                        return@Runnable

                    }

                    Bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}","${args[0]}からの送金")

                    sendMsg(sender,"§a§l送金成功！")

                    val p = Bukkit.getPlayer(uuid)?:return@Runnable
                    sendMsg(p,"§a${sender.name}さんから${format(amount)}円送られました！")
                })

                return true

            }

            "ballog" -> {

                if (sender !is Player)return false

                val page = if (args.isNotEmpty()) args[0].toIntOrNull()?:0 else 0

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val list = Bank.getBankLog(sender,page)

                    sendMsg(sender,"§d§l===========銀行の履歴==========")
                    for (data in list){

                        val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                        sendMsg(sender,"$tag §e${data.dateFormat} §e§l${format(data.amount)} §e${data.note}")

                    }

                    val previous = if (page!=0) {
                        text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/ballog ${page-1}"))
                    }else text(prefix)

                    val next = if (list.size == 10){
                        text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/ballog ${page+1}"))
                    }else text("")

                    sender.sendMessage(previous.append(next))

                })

                return true


            }
        }

        return true
    }

    private fun ZenkakuToHankaku(number: String): Double {

        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)

        return normalize.toDoubleOrNull() ?: return -1.0
    }

    private fun showBalance(sender:Player,p:Player){

        //時差による表示ずれ対策で、一旦所持金を呼び出す

        val loan = ServerLoan.getBorrowingAmount(p)
        val payment = ServerLoan.getPaymentAmount(p)
        val nextDate = ServerLoan.getNextPayTime(p)
        val score = ScoreDatabase.getScore(p.uniqueId)

        val bankAmount = Bank.getBalance(p.uniqueId)
        var cash = -1.0
        var estate = -1.0

        if (p.player != null){
            cash = ATMData.getInventoryMoney(p.player!!) + ATMData.getEnderChestMoney(p.player!!)
            estate = EstateData.getEstate(p)
        }
        sendMsg(sender,"§e§l==========${p.name}のお金==========")
        sendMsg(sender," §b§l電子マネー:  §e§l${format(vault.getBalance(p.uniqueId))}円")
        sendMsg(sender," §b§l銀行:  §e§l${format(bankAmount)}円")
        if (cash>0.0){ sendMsg(sender," §b§l現金:  §e§l${format(cash)}円") }
        if (estate>0.0){ sendMsg(sender," §b§lその他の資産:  §e§l${format(estate)}円") }

        sendMsg(sender," §b§lスコア: §a§l${format(score.toDouble())}")

        if (loan!=0.0 && nextDate!=null){
            sendMsg(sender," §b§lまんじゅうリボ:  §c§l${format(loan)}円")
            sendMsg(sender," §b§l支払額:  §c§l${format(payment)}円")
            sendMsg(sender," §b§l次の支払日: §c§l${SimpleDateFormat("yyyy-MM-dd").format(nextDate.first)}")
            if (nextDate.second>0){
                sendMsg(sender," §c§lMan10リボの支払いに失敗しました(失敗回数:${nextDate.second})。支払いに失敗するとスコアの減少やJailがあります")
            }
        }

        sender.sendMessage(text("$prefix §a§l§n[ここをクリックでコマンドをみる]").clickEvent(ClickEvent.runCommand("/bank help")))

    }

    private fun showCommand(sender:Player){
        val pay = text("$prefix §e[電子マネーを友達に送る]  §n/pay").clickEvent(ClickEvent.suggestCommand("/pay "))
        val atm = text("$prefix §a[電子マネーのチャージ・現金化]  §n/atm").clickEvent(ClickEvent.runCommand("/atm"))
        val deposit = text("$prefix §b[電子マネーを銀行に入れる]  §n/deposit").clickEvent(ClickEvent.suggestCommand("/deposit "))
        val withdraw = text("$prefix §c[電子マネーを銀行から出す]  §n/withdraw").clickEvent(ClickEvent.suggestCommand("/withdraw "))
        val revo = text("$prefix §e[Man10リボを使う]  §n/mrevo borrow").clickEvent(ClickEvent.suggestCommand("/mrevo borrow "))
        val ranking = text("$prefix §6[お金持ちランキング]  §n/mbaltop").clickEvent(ClickEvent.runCommand("/mbaltop"))
        val log = text("$prefix §7[銀行の履歴]  §n/ballog").clickEvent(ClickEvent.runCommand("/ballog"))

        sender.sendMessage(pay)
        sender.sendMessage(atm)
        sender.sendMessage(deposit)
        sender.sendMessage(withdraw)
        sender.sendMessage(revo)
        sender.sendMessage(ranking)
        sender.sendMessage(log)

    }

    @EventHandler
    fun login(e:PlayerJoinEvent){

        Bank.loginProcess(e.player)

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable  {
            Thread.sleep(3000)
            showBalance(e.player,e.player)
        })
    }

    @EventHandler (priority = EventPriority.LOWEST)
    fun logout(e:PlayerQuitEvent){
        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable  {
            EstateData.saveCurrentEstate(e.player)
        })
    }

    @EventHandler
    fun closeEnderChest(e:InventoryCloseEvent){

        if (e.inventory.type != InventoryType.ENDER_CHEST)return

        val p = e.player as Player

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable  { EstateData.saveCurrentEstate(p) })
    }
}