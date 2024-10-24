# -*- coding: utf-8 -*- #

module Rouge
  module Lexers
    load_lexer 'shell.rb'

    class Powershell < Shell
      title 'powershell'
      desc 'powershell'
      tag 'powershell'
      aliases 'posh'
      filenames '*.ps1', '*.psm1', '*.psd1'
      mimetypes 'text/x-powershell'

      ATTRIBUTES = %w(
        CmdletBinding ConfirmImpact DefaultParameterSetName HelpURI SupportsPaging
        SupportsShouldProcess PositionalBinding
      ).join('|')

      KEYWORDS = %w(
        Begin Exit Process Break Filter Return Catch Finally Sequence Class For
        Switch Continue ForEach Throw Data From Trap Define Function Try Do If
        Until DynamicParam In Using Else InlineScript Var ElseIf Parallel While
        End Param Workflow
      ).join('|')

      KEYWORDS_TYPE = %w(
        bool byte char decimal double float int long object sbyte
        short string uint ulong ushort
      ).join('|')

      OPERATORS = %w(
        -split -isplit -csplit -join -is -isnot -as -eq -ieq -ceq -ne -ine
        -cne -gt -igt -cgt -ge -ige -cge -lt -ilt -clt -le -ile -cle -like
        -ilike -clike -notlike -inotlike -cnotlike -match -imatch -cmatch
        -notmatch -inotmatch -cnotmatch -contains -icontains -ccontains
        -notcontains -inotcontains -cnotcontains -replace -ireplace
        -creplace -band -bor -bxor -and -or -xor \. & = \+= -= \*= \/= %=
      ).join('|')

      BUILTINS = %w(
        Add-Content Add-History Add-Member Add-PSSnapin Clear-Content
        Clear-Item Clear-Item Property Clear-Variable Compare-Object
        ConvertFrom-SecureString Convert-Path ConvertTo-Html
        ConvertTo-SecureString Copy-Item Copy-ItemProperty Export-Alias
        Export-Clixml Export-Console Export-Csv ForEach-Object
        Format-Custom Format-List Format-Table Format-Wide
        Get-Acl Get-Alias Get-AuthenticodeSignature Get-ChildItem
        Get-Command Get-Content Get-Credential Get-Culture Get-Date
        Get-EventLog Get-ExecutionPolicy Get-Help Get-History
        Get-Host Get-Item Get-ItemProperty Get-Location Get-Member
        Get-PfxCertificate Get-Process Get-PSDrive Get-PSProvider
        Get-PSSnapin Get-Service Get-TraceSource Get-UICulture
        Get-Unique Get-Variable Get-WmiObject Group-Object
        Import-Alias Import-Clixml Import-Csv Invoke-Expression
        Invoke-History Invoke-Item Join-Path Measure-Command
        Measure-Object Move-Item Move-ItemProperty New-Alias
        New-Item New-ItemProperty New-Object New-PSDrive New-Service
        New-TimeSpan New-Variable Out-Default Out-File Out-Host Out-Null
        Out-Printer Out-String Pop-Location Push-Location Read-Host
        Remove-Item Remove-ItemProperty Remove-PSDrive Remove-PSSnapin
        Remove-Variable Rename-Item Rename-ItemProperty Resolve-Path
        Restart-Service Resume-Service Select-Object Select-String
        Set-Acl Set-Alias Set-AuthenticodeSignature Set-Content Set-Date
        Set-ExecutionPolicy Set-Item Set-ItemProperty Set-Location
        Set-PSDebug Set-Service Set-TraceSource Set-Variable Sort-Object
        Split-Path Start-Service Start-Sleep Start-Transcript Stop-Process
        Stop-Service Stop-Transcript Suspend-Service Tee-Object Test-Path
        Trace-Command Update-FormatData Update-TypeData Where-Object
        Write-Debug Write-Error Write-Host Write-Output Write-Progress
        Write-Verbose Write-Warning ac asnp cat cd chdir clc clear clhy
        cli clp cls clv cnsn compare copy cp cpi cpp curl cvpa dbp del
        diff dir dnsn ebp echo epal epcsv epsn erase etsn exsn fc fl
        foreach ft fw gal gbp gc gci gcm gcs gdr ghy gi gjb gl gm gmo
        gp gps group gsn gsnp gsv gu gv gwmi h history icm iex ihy ii
        ipal ipcsv ipmo ipsn irm ise iwmi iwr kill lp ls man md measure
        mi mount move mp mv nal ndr ni nmo npssc nsn nv ogv oh popd ps
        pushd pwd r rbp rcjb rcsn rd rdr ren ri rjb rm rmdir rmo rni rnp
        rp rsn rsnp rujb rv rvpa rwmi sajb sal saps sasv sbp sc select
        set shcm si sl sleep sls sort sp spjb spps spsv start sujb sv
        swmi tee trcm type wget where wjb write \% \?
      ).join('|')

      prepend :basic do
        rule %r(<#[\s,\S]*?#>)m, Comment::Multiline
        rule /#.*$/, Comment::Single
        rule /\b(#{OPERATORS})\s*\b/i, Operator
        rule /\b(#{ATTRIBUTES})\s*\b/i, Name::Attribute
        rule /\b(#{KEYWORDS})\s*\b/i, Keyword
        rule /\b(#{KEYWORDS_TYPE})\s*\b/i, Keyword::Type
        rule /\bcase\b/, Keyword, :case
        rule /\b(#{BUILTINS})\s*\b(?!\.)/i, Name::Builtin
      end
    end
  end
end
