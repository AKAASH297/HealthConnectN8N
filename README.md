# HealthConnectN8N
Health Connent to Email Health summary and report, with backup to an SQL server

Android Exporter is from https://github.com/angeloanan/HealthConnectExports but modified to provide more information in the exported JSON

https://github.com/AKAASH297/HealthConnectExports-modifed

𝗔𝗿𝗰𝗵𝗶𝘁𝗲𝗰𝘁𝘂𝗿𝗲 𝗢𝘃𝗲𝗿𝘃𝗶𝗲𝘄:

      • Data source: Android Health Connect API (via a modified open-source exporter)
      
      • Workflow / Automation: n8n
      
      • Data storage: PostgreSQL (hosted on Supabase)
      
      • Reporting: SQL-driven analysis with automated PDF generation and email delivery and AI summary

𝗗𝗮𝘁𝗮𝗯𝗮𝘀𝗲 & 𝗦𝗤𝗟 𝗱𝗲𝘀𝗶𝗴𝗻

      • Structured tables for different health metrics
      
      • A date normalization table so weekday / calendar logic is computed once and reused
      
      • INSERT triggers and functions that automatically calculate metrics like sleep efficiency.

𝗢𝘂𝘁𝗽𝘂𝘁 & 𝗮𝘂𝘁𝗼𝗺𝗮𝘁𝗶𝗼𝗻

      • SQL SELECT queries are used to generate daily and weekly analytical views
      
      • Query results are also processed to produce a summary of your week
      
      • A structured PDF report is generated, which is then sent to the user via an email

𝗥𝗲𝗹𝗶𝗮𝗯𝗶𝗹𝗶𝘁𝘆

      • The workflow includes error handling
      
      • Any failure in parsing, SQL, or inserts triggers an error email
      
      • This makes the system hands-off and observable


**Database**
      <img width="1019" height="1089" alt="Screenshot 2026-01-20 234212" src="https://github.com/user-attachments/assets/2db63ab6-3a5a-4d5a-bf8e-a2a7c3b7cc96" />

**N8N Workflow**
      <img width="2098" height="695" alt="Screenshot 2026-01-20 234416" src="https://github.com/user-attachments/assets/71a87a22-8c45-4d59-a8ef-1160e39e9f35" />
      <img width="1960" height="1127" alt="Screenshot 2026-01-20 234546" src="https://github.com/user-attachments/assets/733176b2-d4cf-46f3-8436-4c3307f759ac" />
