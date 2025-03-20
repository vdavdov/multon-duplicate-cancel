

**Конфигурация**
Пример: 

auth:  
    login: (-=====\   //Логин для метода получения jwt  
    password: XxX?XxX  //Пароль для метода получения jwt  
schedule:  
    interval: 60 //Раз во сколько запускается проверка  
host: https://chto-to-tam  



**Логика обработки полных дубликатов:**  
(класс [CheckReportsService](src/main/java/by/vdavdov/service/CheckReportsService.java))  
Отчеты с rejectedReason "Дубликат кода":  

**Получение данных утиля**  
Метод: **processFullDuplicate**  
Эндпоинт: **GET /api/utilization-reports/{reportId}**  
Цель: Проверить количество связанных утилей:  
Если утиль 1 → автоматическая отмена через CancelService  
Если утилей ≥2 → требуется ручная обработка, логируется предупреждение (так как бывают сложные случаи)  

Автоматическая отмена  
Метод: **sentToCancel**  
Отвечает сервис [CancelService](src/main/java/by/vdavdov/service/CancelService.java)  
Эндпоинт - **/code-usage-processor/api/v2/code-usage-reports/reportId/cancel"**  
Отчет переводится в статус "Отменен"  


**Логика обработки повторного нанесения:**  
(класс [CheckReportsService](src/main/java/by/vdavdov/service/CheckReportsService.java))  

Инициализация проверки  
Метод: **getRejectedReports**  
Эндпоинт: GET /code-usage-processor/api/v2/code-usage-reports?query=status==REJECTED;updated>=LAST_RUN_TIME  
LAST_RUN_TIME берется из [LastRunService](src/main/java/by/vdavdov/service/LastRunService.java)  
Авторизация: [AuthService](src/main/java/by/vdavdov/service/AuthService.java) дает JWT  
Хост: [YamlUtil](src/main/java/by/vdavdov/utils/YamlUtil.java) (конфиг)  
Фильтрация причин  
Метод: processPage  
Логика ветвления:  
if (rejectionReason.contains("Повторное нанесение")) → processPartialDuplicate()  
else if (rejectionReason.contains("Дубликат кода")) → processFullDuplicate()  


**Обработка:**  
Метод: **processPartialDuplicate**  
Этапы:
1. Получение кодов отчета (getReportCodes())
2. Поиск связанного утиля (getUtilIdForReport())
3. Анализ ответа регулятора (getLatestResponsePath() → getResponseContent())
4. Циклическое удаление проблемных кодов (deleteCodesFromReport())
5. подтверждение отчета (acceptReport()) + пересчет заказа (recalculateOrder())


**Логирование**  
Log4j2  
log.info("[{}] Успешно обработан", reportId);   
log.error("[{}] Критическая ошибка", reportId, e);  