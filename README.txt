Blackout - Android Studio ছাড়া APK বানানোর নিয়ম

১. github.com এ বিনামূল্যে অ্যাকাউন্ট খুলুন।
২. New repository বানান (নাম: Blackout, Public বা Private)।
৩. "uploading an existing file" লিংকে ক্লিক করে এই জিপ আনজিপ করে ভিতরের সব ফাইল ও ফোল্ডার
   (app, .github, build.gradle.kts, settings.gradle.kts, gradle.properties) টেনে ছেড়ে দিন, Commit করুন।
   (.github ফোল্ডার না উঠলে: Add file > Create new file > নাম লিখুন  .github/workflows/build.yml
    এবং build.yml এর লেখা পেস্ট করুন।)
৪. Actions ট্যাব খুলুন, "Build APK" চলবে (৩-৫ মিনিট)। সবুজ টিক এলে ক্লিক করে নিচে
   Artifacts থেকে Blackout-APK ডাউনলোড করুন।
৫. জিপ খুলে app-debug.apk ফোনে পাঠিয়ে ইনস্টল করুন (Install unknown apps অনুমতি দিন)।
