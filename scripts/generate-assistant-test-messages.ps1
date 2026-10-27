$ErrorActionPreference = 'Stop'
$outputPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'src/test/resources/assistant-fallback-messages.csv'
$outputDirectory = Split-Path $outputPath -Parent
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$rows = [System.Collections.Generic.List[object]]::new()
function Add-Family([string]$Intent, [string[]]$Templates, [string[]]$Suffixes, [string]$Role = 'ANY') {
    foreach ($template in $Templates) {
        foreach ($suffix in $Suffixes) {
            $rows.Add([pscustomobject]@{ role = $Role; modes = 'GEMINI_AND_FALLBACK'; expectedIntent = $Intent; message = ($template + $suffix) })
        }
    }
}
$common = @('', ' please', ' now', ' for me', ' thanks', ' today', ' right now', ' if you can', ' quickly', ' thanks please')
$greetingSuffixes = @('', '!', ' there', ' friend', ' everyone', ' folks', ' please', ' there!', ' today', ' hello')
$short = $common[0..4]

# 600 public/general prompts.
Add-Family 'GREETING' @('hi','hello','hey','good morning','good afternoon') $greetingSuffixes
Add-Family 'CATALOGUE_CATEGORIES' @('list product categories','show categories','what categories do you have','browse store categories','list product types','what categories are available','tell me store categories','show all categories','which product types do you sell','categories in the store') $common
Add-Family 'PRODUCT_INFO' @('tell me details about Summit Waterproof Watch','what is Summit Waterproof Watch','show details for Summit Waterproof Watch','give information about Summit Waterproof Watch','describe Summit Waterproof Watch','price of Summit Waterproof Watch','is Summit Waterproof Watch in stock','tell me about Summit Waterproof Watch','show Summit Waterproof Watch information','what category is Summit Waterproof Watch') $short
Add-Family 'PRODUCT_REVIEWS' @('show reviews for Summit Waterproof Watch','read reviews for Summit Waterproof Watch','reviews of Summit Waterproof Watch','average rating for Summit Waterproof Watch','show ratings for Summit Waterproof Watch','summarize reviews about Summit Waterproof Watch','how is Summit Waterproof Watch rated','what do reviews say about Summit Waterproof Watch','rating of Summit Waterproof Watch','reviews on Summit Waterproof Watch') $short
Add-Family 'SHOPPING_CLEAR' @('show watches under 500','find bags below 200','search jackets under 1000','find shoes over 100','show watches between 50 and 200','waterproof watches under 500','find bags rated 4 stars','show top rated jackets','products under 1000','find footwear rated 5 stars') $common
Add-Family 'SHOPPING_VAGUE' @('something for a trip','something for a gift','help me choose gear','looking for something','need a present') $common
Add-Family 'REWARDS_INFO' @('do i have rewards','show reward points','how many loyalty points do i have','check my reward balance','do you offer loyalty rewards') $common
Add-Family 'GENERAL_QUESTION' @('how do i care for a waterproof jacket','what does gps mean','how do product reviews work','where can i browse categories','how do i add a product to cart') $common
Add-Family 'UNSUPPORTED' @('tell me a joke','what is the capital of France','write a poem','what is the weather today','play a song') $common
Add-Family 'GENERAL_QUESTION' @('how do I choose a watch','what makes a bag waterproof','how are star ratings calculated','how do I compare two products','what should I consider before buying') $common

# 300 role-authorized prompts.
Add-Family 'ACCOUNT_CART' @('show my cart','view my cart','what is in my basket','check cart items','cart total') $common 'CUSTOMER'
Add-Family 'ACCOUNT_ORDERS' @('show my past orders','where is my order','order status','list my order history','recent orders') $common 'CUSTOMER'
Add-Family 'SUPPLIER_INVENTORY' @('show my inventory','list my products','low stock products','my products in stock','products i supply') $common 'SUPPLIER'
Add-Family 'SUPPLIER_ORDERS' @('orders containing my products','show my supplier orders','orders for my products','customer orders with my items','my product orders') $common 'SUPPLIER'
Add-Family 'ADMIN_OVERVIEW' @('show store overview','business overview') $common 'ADMIN'
Add-Family 'ADMIN_ORDERS' @('show all store orders','how many orders') $common 'ADMIN'
Add-Family 'ADMIN_REVIEWS' @('show all reviews','how many reviews') $common 'ADMIN'
Add-Family 'ADMIN_USERS' @('show all users','how many users') $common 'ADMIN'
Add-Family 'ADMIN_SUPPLIERS' @('show all suppliers','how many suppliers') $common 'ADMIN'

# 100 cross-role permission-denial prompts.
Add-Family 'DENY_SUPPLIER_INTENT' @('show store overview','list all users','show all suppliers','show all store orders','show all reviews') $common 'CUSTOMER'
Add-Family 'DENY_ADMIN_INTENT' @('show another customer cart','read customer order history','list every customer purchase','show all customer accounts','show customer private details') $common 'SUPPLIER'

# Additional role/phrasing combinations bring the evaluation to 2,000 total cases.
$roleVariants = @('', ' please', ' now', ' right now', ' for me', ' today', ' if possible', ' summarize', ' give me a count', ' thanks')
Add-Family 'ACCOUNT_CART' @('contents of my cart','how much is in my cart','show cart items','what did i add to my cart','cart items') $roleVariants 'CUSTOMER'
Add-Family 'ACCOUNT_ORDERS' @('my order list','orders i have placed','show completed orders','show pending orders','order history for my account') $roleVariants 'CUSTOMER'
Add-Family 'ACCOUNT_ORDERS' @('show my delivered orders','show my shipped orders','show my cancelled orders','what is the status of my order','when will my order arrive') $roleVariants 'CUSTOMER'
Add-Family 'SUPPLIER_INVENTORY' @('which of my products are low stock','how many products do i supply','show my product stock','list my supplier catalogue','inventory for my business','which products need restocking','show products assigned to me','my available products','count my inventory','check stock for my listings') $roleVariants 'SUPPLIER'
Add-Family 'SUPPLIER_ORDERS' @('show orders that include my products','list orders containing my listings','how many customer orders include my products','show order statuses for my products','orders from my catalogue','which orders have my items','show my fulfilled product orders','list recent orders for my products','count orders containing supplier items','show supplier order activity') $roleVariants 'SUPPLIER'
Add-Family 'ADMIN_OVERVIEW' @('summarize the whole store','give me store metrics','overview of all store activity','show business totals','admin store summary','give me the store dashboard numbers','overall store status','show key store counts','summarize catalogue and users','how is the store doing') $roleVariants[0..4] 'ADMIN'
Add-Family 'ADMIN_ORDERS' @('count all customer orders','show store order totals','summarize order statuses','how many orders were placed','list overall order counts','show cancelled order count','show delivered order count','storewide order report','give total order statistics','summarize all orders') $roleVariants[0..4] 'ADMIN'
Add-Family 'ADMIN_REVIEWS' @('count all product reviews','show overall ratings summary','summarize review activity','how many ratings are recorded','storewide review count','show rating totals','review statistics for all products','overall customer feedback count','give review metrics','summarize ratings across products') $roleVariants[0..4] 'ADMIN'
Add-Family 'ADMIN_USERS' @('count registered users','show customer account totals','summarize user accounts','how many customer accounts exist','user totals by store','registered account count','show user statistics','count all accounts','storewide user summary','give user metrics') $roleVariants[0..4] 'ADMIN'
Add-Family 'ADMIN_SUPPLIERS' @('count registered suppliers','show supplier totals','summarize supplier accounts','how many vendors are registered','supplier statistics','show vendor account count','count all suppliers','storewide supplier summary','give supplier metrics','list supplier totals') $roleVariants[0..4] 'ADMIN'
Add-Family 'DENY_SUPPLIER_INTENT' @('show all customer orders','read all user profiles','show storewide user counts','list every suppliers products','show admin dashboard metrics','show all reviews in the store','count every user','list every customer order','show global inventory','show account data for all users') $roleVariants 'CUSTOMER'
Add-Family 'DENY_ADMIN_INTENT' @('show customer 8 cart','read supplier 3 private inventory','show another users order history','list a customers purchases','show a different suppliers orders','read customer cart contents','show private customer details','give me supplier personal information','list products for another supplier','show another account purchases') $roleVariants 'SUPPLIER'
Add-Family 'ACCOUNT_CART' @('show cart total','how many items are in my basket','list what i added to cart','review items in my cart','cart contents for my account','tell me my cart subtotal','show my current basket','what products are in my cart','summarize cart items','check my shopping basket') $roleVariants 'CUSTOMER'
Add-Family 'ADMIN_OVERVIEW' @('give full store overview','show store totals','summarize business activity','overview of products orders and users','admin summary of the shop','show total store records','storewide dashboard summary','give me all business counts','summarize Cart Rush operations','show complete store metrics') $roleVariants 'ADMIN'

if ($rows.Count -ne 2000) { throw "Expected exactly 2000 rows (1000 general + 1000 role-aware), generated $($rows.Count)." }
$rows | Export-Csv -LiteralPath $outputPath -NoTypeInformation -Encoding utf8
Write-Output "Generated $($rows.Count) test messages (1000 general + 1000 role-aware): $outputPath"
