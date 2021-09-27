package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.OP
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.loan.ServerLoan
import java.util.concurrent.ConcurrentHashMap

class TestCommand : CommandExecutor{

    private val amount = 100.0

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="baltest"){return false}

        if (sender !is Player)return true

        if (!sender.hasPermission(OP))return true

        if (args.isEmpty()){

            sendMsg(sender,"/baltest single <count>")
            sendMsg(sender,"/baltest multi <count>")
            return true
        }

        if (args[0] == "single"){

            val count = args[1].toIntOrNull()?:return true
            val uuid = sender.uniqueId

            vault.withdraw(uuid, vault.getBalance(uuid))

            vault.deposit(uuid, (count*amount))

            val depositRets = HashMap<Int,Int>()
            val withdrawRets = HashMap<Int,Int>()

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

                sender.sendMessage("StartDeposit")

                for (i in 0 until count){
                    val ret = Bank.deposit(uuid,amount, plugin,"BankTest","Man10Bankのテスト").first
                    depositRets[ret] = (depositRets[ret]?:0)+1
                }

                sender.sendMessage("StartWithdraw")

                for (i in 0 until count){
                    val ret = Bank.withdraw(uuid,amount, plugin,"BankTest","Man10Bankのテスト").first
                    withdrawRets[ret] = (withdrawRets[ret]?:0)+1
                }

                sender.sendMessage("入金の結果")
                for (ret in depositRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                sender.sendMessage("出金の結果")
                for (ret in withdrawRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                sender.sendMessage("Finish")
            })


            return true
        }

        if (args[0] == "multi"){

            val count = args[1].toIntOrNull()?:return true
            val uuid = sender.uniqueId

            vault.withdraw(uuid, vault.getBalance(uuid))

            vault.deposit(uuid, (count*amount))

            val depositRets = ConcurrentHashMap<Int,Int>()
            val withdrawRets = ConcurrentHashMap<Int,Int>()

            val core = Runtime.getRuntime().availableProcessors()

            val oneCount = count/core

            var thread = core

            for (c in 0 until core){
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    for (i in 0 until oneCount){
                        Bank.asyncDeposit(uuid,amount, plugin,"BankTest","Man10Bankのテスト"){ code:Int,_:Double,_:String ->
                            depositRets[code] = (depositRets[code]?:0)+1
                        }
                    }
                    thread--
                })
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

                while (thread!=0){Thread.sleep(1)}

                Bukkit.getLogger().info("入金キュー完了")

                thread = core

                var tCount = 0

                for (c in 0 until core){
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        for (i in 0 until oneCount){
                            Bank.asyncWithdraw(uuid,amount, plugin,"BankTest","Man10Bankのテスト") { code: Int, _: Double, _: String ->
                                withdrawRets[code] = (withdrawRets[code]?:0)+1
                                tCount ++
                            }
                        }
                        thread--
                    })
                }

                while (thread!=0){Thread.sleep(1)}

                Bukkit.getLogger().info("出金キュー完了")

                while (tCount<count){Thread.sleep(1)}


                ///////終了処理//////////
                sender.sendMessage("入金の結果")
                for (ret in depositRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                sender.sendMessage("出金の結果")
                for (ret in withdrawRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }
                sender.sendMessage("論理プロセッサ数:${core}")
                sender.sendMessage("Finish")
            })

            return true

        }

        if (args[0] == "revo") {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                ServerLoan.batch()
            })
        }

        if (args[0] == "login"){

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                for (i in 0 until  (args[1].toIntOrNull()?:100)){
                    Bank.loginProcess(sender)
                }
            })
        }

        if (args[0] == "test"){

            for (i in 0 until  Runtime.getRuntime().availableProcessors()){
                Thread{
                    sendMsg(sender,"スリープ開始 $i")
                    Thread.sleep(100000)
                    sendMsg(sender,"スリープ終了 $i")
                }.start()
            }

            Thread{
                sendMsg(sender,"TEST2")
            }.start()

        }

        return false
    }


}