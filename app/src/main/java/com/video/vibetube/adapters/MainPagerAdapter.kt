package com.video.vibetube.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.video.vibetube.fragments.*

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val homeChannelList = listOf(
        Pair("UC7ITT3ooYWDYY_ehIUbt6eg", "Asian Cricket Council")
       /* Pair("UCFFbwnve3yF62-tVXkTyHqg", "Zee Music Company"),
        Pair("UCq-Fj5jknLsUf-MWSy4_brA", "T-Series"),
        Pair("UC7krt1E6XvrywJBu0ZOyq3Q", "Khan GS Research Centre"),
        Pair("UCatL-c6pmnjzEOHSyjn-sHA", "khan Global Studies"),
        Pair("UCVF6ZS4auveaH2vNY5Ajipw", "Gyan Ganga"),
        Pair("UC-CSyyi47VX1lD9zyeABW3w", "Dhruv Rathee"),
        Pair("UCzwCEE_PchiBULMnAJqhGVg", "Raj Shamani"),
        Pair("UCYalWwW79BdxmMTKUyBFj5g", "Shubhankar Mishra"),
        Pair("UCiGyWN6DEbnj2alu7iapuKQ", "Physics Wallah"),
        Pair("UCzLqOSZPtUKrmSEnlH4LAvw", "Drishti IAS"),
        Pair("UCt2JXOLNxqry7B_4rRZME3Q", "ICC"),
        Pair("UCJ5v_MCY6GNUBTO8-D3XoAg", "WWE"),
        Pair("UC1L2JoMpcY6MRLhFd3gg5Xg", "Adda247"),
        Pair("UCDnq05Q89oYq-Tz5boL73Tw", "Curly Tales"),
        Pair("UCt4t-jeY85JegMlZ-E5UWtA", "Aaj Tak"),
        Pair("UCx8Z14PpntdaxCt2hakbQLQ", "The Lallantop"),
        Pair("UCOutOIcn_oho8pyVN3Ng-Pg", "TV9 Bharatvarsh"),
        Pair("UCmTM_hPCeckqN3cPWtYZZcg", "The Deshbhakt"),
        Pair("UCChqsCRFePrP2X897iQkyAA", "Kabita’s Kitchen"),
        Pair("UC295-Dw_tDNtZXFeAPAW6Aw", "5-Minute Crafts"),
        Pair("UCx2JNgWJPPTBODN-U56O-tg", "Komal Pandey"),
        Pair("UCbCmjCuTUZos6Inko4u57UQ", "Cocomelon - Nursery Rhymes"),*/

        )
    private val shortStoriesChannelList = listOf(
        Pair("UC1NtcHxG3wiyhtbbPmIaMnA", "Amazon MX Player")
       /* Pair("UCNkivqvTLBc1THA_dG2K7-A", "The Asstag"),
        Pair("UCQAyprC49RmdPXzDhmCXUBA", "The Macho Bird"),
        Pair("UCNJcSUSzUeFm8W9P7UUlSeQ", "The Viral Fever"),
        Pair("UC7IMq6lLHbptAnSucW1pClA", "FilterCopy"),
        Pair("UCTlnaHHQ75zlDg_fLr7tGEg", "Timeliners"),
        Pair("UCC89oVFqenaffhBoyqxd6qw", "Dice Media")*/
    )
    private val comedyChannelList = listOf(
        Pair("UC6-F5tO8uklgE9Zy8IvbdFw", "Sony SAB")
        /*Pair("UClyw32fdzLCsqbkfzhdrtXA", "Keshav Shashi Vlogs"),
        Pair("UCr8x9Ve9KWnTmfjO5JGwEhQ", "Kapil Kanpuria")*/
        /*Pair("UCFFbwnve3yF62-tVXkTyHqg", "Amit Bhadana"),
        Pair("UCbTLwN10NoCU4WDzLf1JMOA", "Kapil Sharma"),
        Pair("UCFFbwnve3yF62-tVXkTyHqg", "Ashish Chanchlani Vines"),
        Pair("UC56gTxNs4f9xZ7Pa2i5xNzg", "Harsh Beniwal"),
        Pair("UCq-Fj5jknLsUf-MWSy4_brA", "CarryMinati"),
        Pair("UC_A7K2dXFsTMAciGmnNxy-Q", "MJO"),
        Pair("UCOsyDsO5tIt-VZ1iwjdQmew", "BB Ki Vines")*/
    )

    private val moviesChannelList = listOf(
        Pair("UCyoXW-Dse7fURq30EWl_CUA", "Goldmines Telefilms")/*,
        Pair("UCCrw5RBWF9i8VPt4IxL8IsQ", "Goldmines Cineplex"),
        Pair("UCY7tB-mpvlQeWgaCCS1Cmwg", "Goldmines Action"),
        Pair("UCIobY5Y-ly9Bo458yjy36OQ", "B4U Bhojpuri")*/
        /*Pair("UC56gTxNs4f9xZ7Pa2i5xNzg", "Wamindia Movies"),
        Pair("UC56gTxNs4f9xZ7Pa2i5xNzg", "Aditya Movies"),
        Pair("UCbTLwN10NoCU4WDzLf1JMOA", "Pen Movies"),
        Pair("UCq-Fj5jknLsUf-MWSy4_brA", "Venus Entertainment"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "Zee Bollywood"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "Filmy Duniya"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "SET India")*/
    )

    private val vloggersChannelList = listOf(
        Pair("UCFFbwnve3yF62-tVXkTyHqg", "Sourav Joshi Vlogs"),
        Pair("UCFFbwnve3yF62-tVXkTyHqg", "MrBeast"),
        Pair("UCFFbwnve3yF62-tVXkTyHqg", "Flying Beast"),
        Pair("UC56gTxNs4f9xZ7Pa2i5xNzg", "Farah Khan"),
        Pair("UC56gTxNs4f9xZ7Pa2i5xNzg", "Triggered Insaan"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "Technical Guruji"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "Lakshay Chaudhary Vlogs"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "Jatt Prabhjot"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "Village Cooking Channel")
    )
    private val scienceChannelList = listOf(
        Pair("UCSiDGb0MnHFGjs4E2WKvShw", "Mr Indian Hacker")/*,
        Pair("UCebC4x5l2-PQxg46Ucv9CsA", "Crazy XYZ"),
        Pair("UC00ifCvU8YOOzbL3RdiSTDw", "Getsetfly Science")*/
        /*Pair("UC56gTxNs4f9xZ7Pa2i5xNzg", "Gareeb Scientist"),
        Pair("UCbTLwN10NoCU4WDzLf1JMOA", "Experiment King"),
        Pair("UCq-Fj5jknLsUf-MWSy4_brA", "The Indian Unboxer"),
        Pair("UC22nIfOTM7KLIQuFGMKzQbg", "TechRider Experiment"),*/
    )
    private val fragments = listOf(
        HomeFragment.newInstance(homeChannelList),
        ShortSeriesFragment.newInstance(shortStoriesChannelList),
        ComedyFragment.newInstance(comedyChannelList),
        TrendingMoviesFragment.newInstance(moviesChannelList),
        DIYFragment.newInstance(scienceChannelList)
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

}