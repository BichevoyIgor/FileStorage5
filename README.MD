**Сетевое файловое хранилище**

Проект представляет клиент-серверное сетевое хранилище и состоит из двух основных можулей: Client и Server.

Сетевое взаиможействие между клиентом и сервером осуществляется с использованием IO.

**Client** 
Клиентская часть реализована на JDK 15. Пользовательский интерфейс на основе JavaFX 11.0.2
Главное окно содержит кнопки управления и два списка файлов.
Левый список отображает только файлы и папки пользователя на сервере. Каталог пользователя создается при регистрации.
Правый список отображает файлы и папки на ПК пользователя.

**Server**
Серверная часть реализована на JDK 15.

**Основные функции приложения:**
Кнопки управления позволяют:
"Копировать файл" - копирование выделенного файла  
"Переместить файл" - перемещение выделенного файла
"Обновить" - принудительное обновление списков
"Удалить" - удаление выделенного файла или директории
"вверх" - перемещение вверх по структуре каталогов
"создать папку" - создание папки

Меню Файл содержит пункты:
"Закрыть программу" - закрытие окна программы
"Выход" -  выход текущего пользователя из сессии на сервере
"Авториция" -  пункт доступен только в том случае если авторизация не пройдена

Окно авторизации соджержит следующие элементы:
"поле login" - служит для ввода логина пользователя
"поле password" - служит для ввода пароля пользователя
"Зарегистрироваться" - регистрация пользователя на сервере
"Авторизоваться" - авторизация пользователя на сервере




