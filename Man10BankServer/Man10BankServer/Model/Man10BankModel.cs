using System.Collections.Concurrent;
using System.ComponentModel.DataAnnotations;
using Microsoft.EntityFrameworkCore;

namespace Man10BankServer.Model;

/// <summary>
/// UserBankテーブル
/// </summary>
public class UserBank
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public double balance { get; set; }

}

/// <summary>
/// 銀行ログのテーブル
/// </summary>
public class MoneyLog
{
    
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public string plugin_name { get; set; }
    public double amount { get; set; }
    public string note { get; set; }
    public string display_note { get; set; }
    public string server { get; set; }
    public bool deposit { get; set; }
    public DateTime date { get; set; }
}

/// <summary>
/// ATMのログ
/// </summary>
public class ATMLog
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public double amount { get; set; }
    public bool deposit { get; set; }
    public DateTime date { get; set; }
}

/// <summary>
/// ユーザー間借金のテーブル
/// </summary>
public class LocalLoanTable
{
    [Key]
    public int id { get; set; }
    public string lend_player { get; set; }
    public string lend_uuid { get; set; }
    public string borrow_player { get; set; }
    public string borrow_uuid { get; set; }
    public DateTime borrow_date { get; set; }
    public DateTime payback_date { get; set; }
    public double amount { get; set; }
}

/// <summary>
/// 最新の資産テーブル
/// </summary>
public class EstateTable
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public DateTime date { get; set; }
    public double vault { get; set; }
    public double bank { get; set; }
    public double cash { get; set; }
    public double estate { get; set; }
    public double loan { get; set; }
    public double shop { get; set; }
    public double crypto { get; set; }
    public double total { get; set; }
}

/// <summary>
/// 資産履歴テーブル
/// </summary>
public class EstateHistoryTable
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public DateTime date { get; set; }
    public double vault { get; set; }
    public double bank { get; set; }
    public double cash { get; set; }
    public double estate { get; set; }
    public double loan { get; set; }
    public double shop { get; set; }
    public double crypto { get; set; }
    public double total { get; set; }
}

/// <summary>
/// サーバー全体の資産ヒストリー
/// </summary>
public class ServerEstateHistory
{
    [Key]
    public int id { get; set; }
    public double vault { get; set; }
    public double bank { get; set; }
    public double cash { get; set; }
    public double estate { get; set; }
    public double loan { get; set; }
    public double shop { get; set; }
    public double crypto { get; set; }
    public double total { get; set; }
    public int year { get; set; }
    public int month { get; set; }
    public int day { get; set; }
    public int hour { get; set; }
    public DateTime date { get; set; }
    
}

/// <summary>
/// 小切手のテーブル
/// </summary>
public class ChequeTable
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public double amount { get; set; }
    public string note { get; set; }
    public DateTime date { get; set; }
    public DateTime use_date { get; set; }
    public bool used { get; set; }
}


/// <summary>
/// リボのテーブル
/// </summary>
public class ServerLoanTable
{
    [Key]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public DateTime borrow_date { get; set; }
    public DateTime last_pay_date { get; set; }
    public double borrow_amount { get; set; }
    public double payment_amount { get; set; }
    public int failed_payment { get; set; }
    public bool stop_interest { get; set; }
}

/// <summary>
/// 電子マネーのログテーブル
/// </summary>
public class VaultLog
{
    [Key]
    public int id { get; set; }
    public string from_player { get; set; }
    public string from_uuid { get; set; }
    public string to_player { get; set; }
    public string to_uuid { get; set; }
    public double amount { get; set; }
    public string plugin { get; set; }
    public string note { get; set; }
    public string display_note { get; set; }
    public string category { get; set; }
    public DateTime date { get; set; }
    
}

public class Context : DbContext
{
    
    public DbSet<UserBank> user_bank { get; set; }
    public DbSet<MoneyLog> money_log { get; set; }
    public DbSet<ATMLog> atm_log { get; set; }
    public DbSet<LocalLoanTable> loan_table { get; set; }
    public DbSet<EstateTable> estate_tbl { get; set; }
    public DbSet<EstateHistoryTable> estate_history_tbl { get; set; }
    public DbSet<ServerEstateHistory> server_estate_history { get; set; }
    public DbSet<ChequeTable> cheque_tbl { get; set; }
    public DbSet<ServerLoanTable> server_loan_tbl { get; set; }
    public DbSet<VaultLog> vault_log { get; set; }

    private static readonly BlockingCollection<Action<Context>> DbQueue = new();

    private static string Host { get; set; }
    private static string Port { get; set; }
    private static string Pass { get; set; }
    private static string User { get; set; }
    private static string DatabaseName { get; set; }

    /// <summary>
    /// DBの接続設定を読み込む
    /// </summary>
    static Context()
    {
        Host = "localhost";
        Port = "3306";
        Pass = "rDcrmPRLJvu@ex/E,>K";
        User = "forest";
        DatabaseName = "man10_bank";

        Task.Run(RunDatabaseQueue);
    }

    public static void SetDatabase(IConfiguration config)
    {
        Host = config["MySQL:Host"] ?? "";
        Port = config["MySQL:Port"] ?? "";
        Pass = config["MySQL:Pass"] ?? "";
        User = config["MySQL:User"] ?? "";
        DatabaseName = config["MySQL:DatabaseName"] ?? "";
    }
    
    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        var connectionString = $"server='{Host}';port='{Port}';user='{User}';password='{Pass}';Database='{DatabaseName}'";
        var serverVersion = new MySqlServerVersion(new Version(8, 0, 30));
        optionsBuilder.UseMySql(connectionString, serverVersion);
    }


    private static void RunDatabaseQueue()
    {
        var context = new Context();
        Console.WriteLine("データベースキューを起動");
        while (true)
        {
            try
            {
                DbQueue.TryTake(out var job);
                job?.Invoke(context);
            }
            catch (Exception e)
            {
                // ignored
                Console.WriteLine(e.Message);
            }
        }
    }

    /// <summary>
    /// ログなどの即効性を求めないクエリを投げるためのキュー
    /// </summary>
    public static void AddDatabaseJob(Action<Context> job)
    {
        DbQueue.Add(job);
    }
}
